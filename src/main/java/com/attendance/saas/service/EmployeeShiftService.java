package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.shift.AssignShiftRequest;
import com.attendance.saas.dto.shift.EmployeeShiftResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.EmployeeShift;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.ShiftMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeShiftRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Roster management: which shift each employee works on each date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeShiftService {

    /** Guard against a single request writing an unbounded roster. */
    private static final int MAX_RANGE_DAYS = 366;

    private final EmployeeShiftRepository employeeShiftRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeService employeeService;
    private final ShiftService shiftService;
    private final ShiftMapper shiftMapper;
    private final AuditService auditService;

    /**
     * Assigns one shift across a date range. Existing entries are kept unless
     * {@code overwriteExisting} is set, so a bulk assignment cannot silently
     * wipe a hand-tuned roster.
     */
    @Transactional
    public List<EmployeeShiftResponse> assign(AssignShiftRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        validateRange(request.startDate(), request.endDate());

        Employee employee = employeeService.requireInCompany(request.employeeId(), companyId);
        Shift shift = shiftService.requireInCompany(request.shiftId(), companyId);
        Company company = companyRepository.getReferenceById(companyId);
        boolean overwrite = Boolean.TRUE.equals(request.overwriteExisting());

        List<EmployeeShift> existing = employeeShiftRepository.findAssignmentsInRange(
                companyId, employee.getId(), request.startDate(), request.endDate());

        if (!existing.isEmpty() && !overwrite) {
            throw new ConflictException(
                    ErrorCode.SHIFT_ASSIGNMENT_ALREADY_EXISTS,
                    "Sudah ada %d jadwal pada rentang tersebut, gunakan overwriteExisting=true"
                            .formatted(existing.size()));
        }

        List<EmployeeShift> saved = new ArrayList<>();
        for (LocalDate date = request.startDate();
             !date.isAfter(request.endDate());
             date = date.plusDays(1)) {

            LocalDate current = date;
            EmployeeShift assignment = existing.stream()
                    .filter(entry -> entry.getShiftDate().equals(current))
                    .findFirst()
                    .orElseGet(() -> EmployeeShift.builder()
                            .company(company)
                            .employee(employee)
                            .shiftDate(current)
                            .build());
            assignment.setShift(shift);
            saved.add(employeeShiftRepository.save(assignment));
        }

        auditService.record(AuditAction.CREATE, "EmployeeShift", shift.getId(),
                "Menjadwalkan %s untuk %s, %s s/d %s".formatted(
                        shift.getName(), employee.getEmployeeCode(),
                        request.startDate(), request.endDate()));
        log.info("Assigned shift {} to employee {} for {} day(s) in company {}",
                shift.getId(), employee.getId(), saved.size(), companyId);

        return saved.stream().map(shiftMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeShiftResponse> search(Long employeeId,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        validateRange(startDate, endDate);

        if (employeeId != null) {
            // Fails fast with EMPLOYEE_NOT_FOUND if the id belongs elsewhere.
            employeeService.requireInCompany(employeeId, companyId);
        }

        return PageResponse.of(
                employeeShiftRepository.search(companyId, employeeId, startDate, endDate, pageable),
                shiftMapper::toResponse);
    }

    /** Roster of the employee behind the current account. */
    @Transactional(readOnly = true)
    public List<EmployeeShiftResponse> myRoster(LocalDate startDate, LocalDate endDate) {
        Long companyId = SecurityUtils.requireCompanyId();
        validateRange(startDate, endDate);
        Employee employee = employeeService.requireOwnEmployee();

        return employeeShiftRepository
                .findAssignmentsInRange(companyId, employee.getId(), startDate, endDate)
                .stream()
                .map(shiftMapper::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long assignmentId) {
        Long companyId = SecurityUtils.requireCompanyId();
        EmployeeShift assignment = employeeShiftRepository.findByIdAndCompany_Id(assignmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SHIFT_NOT_FOUND, "Jadwal shift tidak ditemukan"));

        employeeShiftRepository.delete(assignment);
        auditService.record(AuditAction.DELETE, "EmployeeShift", assignmentId,
                "Menghapus jadwal " + assignment.getShiftDate());
        log.info("Shift assignment {} deleted from company {}", assignmentId, companyId);
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE, "Tanggal mulai dan tanggal selesai wajib diisi");
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Tanggal selesai tidak boleh lebih awal dari tanggal mulai");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Rentang tanggal maksimal " + MAX_RANGE_DAYS + " hari");
        }
    }
}
