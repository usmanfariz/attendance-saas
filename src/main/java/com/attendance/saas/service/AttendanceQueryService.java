package com.attendance.saas.service;

import com.attendance.saas.dto.attendance.AttendanceResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.AttendanceMapper;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.security.SecurityUtils;
import com.attendance.saas.specification.AttendanceSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Read side of attendance. Kept apart from {@link AttendanceService} so the
 * check-in/check-out rules are not diluted by reporting concerns.
 */
@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

    private final AttendanceRepository attendanceRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeService employeeService;
    private final AttendanceMapper attendanceMapper;

    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> search(Long employeeId,
                                                   Long departmentId,
                                                   Long shiftId,
                                                   AttendanceStatus status,
                                                   LocalDate startDate,
                                                   LocalDate endDate,
                                                   Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        validateRange(startDate, endDate);

        if (employeeId != null) {
            employeeService.requireInCompany(employeeId, companyId);
        }

        Page<Attendance> page = attendanceRepository.findAll(specification(
                companyId, employeeId, departmentId, shiftId, status, startDate, endDate), pageable);

        ZoneId zone = zoneOf(companyId);
        return PageResponse.of(page, attendance -> attendanceMapper.toResponse(attendance, zone));
    }

    /** History of the employee behind the current account. */
    @Transactional(readOnly = true)
    public PageResponse<AttendanceResponse> myHistory(LocalDate startDate,
                                                      LocalDate endDate,
                                                      Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        validateRange(startDate, endDate);
        Long employeeId = employeeService.requireOwnEmployee().getId();

        Page<Attendance> page = attendanceRepository.findAll(specification(
                companyId, employeeId, null, null, null, startDate, endDate), pageable);

        ZoneId zone = zoneOf(companyId);
        return PageResponse.of(page, attendance -> attendanceMapper.toResponse(attendance, zone));
    }

    @Transactional(readOnly = true)
    public AttendanceResponse getById(Long attendanceId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Attendance attendance = attendanceRepository.findByIdAndCompany_Id(attendanceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ATTENDANCE_NOT_FOUND, "Data absensi tidak ditemukan"));

        return attendanceMapper.toResponse(attendance, zoneOf(companyId));
    }

    private Specification<Attendance> specification(Long companyId,
                                                    Long employeeId,
                                                    Long departmentId,
                                                    Long shiftId,
                                                    AttendanceStatus status,
                                                    LocalDate startDate,
                                                    LocalDate endDate) {
        return AttendanceSpecifications.ofCompany(companyId)
                .and(AttendanceSpecifications.ofEmployee(employeeId))
                .and(AttendanceSpecifications.ofDepartment(departmentId))
                .and(AttendanceSpecifications.ofShift(shiftId))
                .and(AttendanceSpecifications.hasStatus(status))
                .and(AttendanceSpecifications.dateFrom(startDate))
                .and(AttendanceSpecifications.dateTo(endDate))
                .and(AttendanceSpecifications.withAssociationsFetched());
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Tanggal selesai tidak boleh lebih awal dari tanggal mulai");
        }
    }

    private ZoneId zoneOf(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.COMPANY_NOT_FOUND, "Perusahaan tidak ditemukan"));
        return company.zoneId();
    }
}
