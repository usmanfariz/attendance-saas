package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.shift.ShiftRequest;
import com.attendance.saas.dto.shift.ShiftResponse;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.ShiftMapper;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeShiftRepository;
import com.attendance.saas.repository.ShiftRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final AttendanceRepository attendanceRepository;
    private final CompanyRepository companyRepository;
    private final ShiftMapper shiftMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ShiftResponse> search(String keyword, org.springframework.data.domain.Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResponse.of(
                shiftRepository.search(companyId, normalized, pageable), shiftMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listAll() {
        return shiftRepository.findByCompany_IdOrderByStartTimeAsc(SecurityUtils.requireCompanyId())
                .stream()
                .map(shiftMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse getById(Long shiftId) {
        return shiftMapper.toResponse(load(shiftId, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public ShiftResponse create(ShiftRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        String name = request.name().trim();

        if (shiftRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new ConflictException(
                    ErrorCode.SHIFT_NAME_ALREADY_EXISTS, "Nama shift sudah digunakan");
        }
        validateTimes(request.startTime(), request.endTime());

        Shift shift = Shift.builder()
                .company(companyRepository.getReferenceById(companyId))
                .name(name)
                .startTime(request.startTime())
                .endTime(request.endTime())
                .lateToleranceMinutes(orZero(request.lateToleranceMinutes()))
                .earlyLeaveToleranceMinutes(orZero(request.earlyLeaveToleranceMinutes()))
                .defaultShift(Boolean.TRUE.equals(request.defaultShift()))
                .build();
        shiftRepository.save(shift);
        enforceSingleDefault(companyId, shift);

        auditService.record(AuditAction.CREATE, "Shift", shift.getId(),
                "Menambah shift %s (%s - %s)".formatted(name, shift.getStartTime(), shift.getEndTime()));
        log.info("Shift {} ({} - {}) created in company {}",
                shift.getId(), shift.getStartTime(), shift.getEndTime(), companyId);
        return shiftMapper.toResponse(shift);
    }

    @Transactional
    public ShiftResponse update(Long shiftId, ShiftRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Shift shift = load(shiftId, companyId);
        String name = request.name().trim();

        if (shiftRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, name, shiftId)) {
            throw new ConflictException(
                    ErrorCode.SHIFT_NAME_ALREADY_EXISTS, "Nama shift sudah digunakan");
        }
        validateTimes(request.startTime(), request.endTime());

        shift.setName(name);
        shift.setStartTime(request.startTime());
        shift.setEndTime(request.endTime());
        shift.setLateToleranceMinutes(orZero(request.lateToleranceMinutes()));
        shift.setEarlyLeaveToleranceMinutes(orZero(request.earlyLeaveToleranceMinutes()));
        shift.setDefaultShift(Boolean.TRUE.equals(request.defaultShift()));
        enforceSingleDefault(companyId, shift);

        auditService.record(AuditAction.UPDATE, "Shift", shiftId, "Mengubah shift " + name);
        log.info("Shift {} updated in company {}", shiftId, companyId);
        return shiftMapper.toResponse(shift);
    }

    /**
     * Deleting a shift that is still rostered or already referenced by
     * attendance would orphan history, so it is refused.
     */
    @Transactional
    public void delete(Long shiftId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Shift shift = load(shiftId, companyId);

        long rostered = employeeShiftRepository.countByShift_Id(shiftId);
        long recorded = attendanceRepository.countByShift_Id(shiftId);
        if (rostered > 0 || recorded > 0) {
            throw new ConflictException(
                    ErrorCode.SHIFT_IN_USE,
                    "Shift masih dipakai pada %d jadwal dan %d data absensi".formatted(rostered, recorded));
        }

        shiftRepository.delete(shift);
        auditService.record(AuditAction.DELETE, "Shift", shiftId,
                "Menghapus shift " + shift.getName());
        log.info("Shift {} deleted from company {}", shiftId, companyId);
    }

    @Transactional(readOnly = true)
    public Shift requireInCompany(Long shiftId, Long companyId) {
        return load(shiftId, companyId);
    }

    private Shift load(Long shiftId, Long companyId) {
        return shiftRepository.findByIdAndCompany_Id(shiftId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SHIFT_NOT_FOUND, "Shift tidak ditemukan"));
    }

    /**
     * A zero-length shift would make every calculation meaningless; an end time
     * before the start is legitimate and simply means the shift runs overnight.
     */
    private void validateTimes(LocalTime startTime, LocalTime endTime) {
        if (startTime.equals(endTime)) {
            throw new BusinessException(
                    ErrorCode.INVALID_SHIFT, "Jam mulai dan jam selesai tidak boleh sama");
        }
    }

    private void enforceSingleDefault(Long companyId, Shift shift) {
        if (shift.isDefaultShift()) {
            shiftRepository.clearDefaultExcept(companyId, shift.getId());
        }
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
