package com.attendance.saas.service;

import com.attendance.saas.dto.report.DailyAttendanceReportResponse;
import com.attendance.saas.dto.report.EmployeeAttendanceReportResponse;
import com.attendance.saas.dto.report.EmployeeAttendanceSummary;
import com.attendance.saas.dto.report.MonthlyAttendanceReportResponse;
import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.AttendanceMapper;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.EmployeeShiftRepository;
import com.attendance.saas.repository.ShiftRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Attendance reporting: one day across the company, one month per employee, and
 * one employee across a range.
 *
 * <p>Aggregation happens in the database; this service only shapes the result
 * and applies the tenant scope.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Keeps a single request from scanning an unbounded slice of history. */
    private static final int MAX_RANGE_DAYS = 366;

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final ShiftRepository shiftRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeService employeeService;
    private final AttendanceMapper attendanceMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DailyAttendanceReportResponse daily(LocalDate requestedDate,
                                               Long departmentId,
                                               Long shiftId,
                                               AttendanceStatus status) {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        ZoneId zone = company.zoneId();
        LocalDate date = requestedDate != null ? requestedDate : today(company);

        List<Attendance> attendances =
                attendanceRepository.findForDailyReport(companyId, date, departmentId, shiftId);

        List<DailyAttendanceReportResponse.Row> rows = attendances.stream()
                .filter(attendance -> status == null || attendance.getStatus() == status)
                .map(attendance -> toRow(attendance, zone))
                .toList();

        long totalEmployees = employeeRepository.countByCompany_IdAndStatus(
                companyId, EmployeeStatus.ACTIVE);
        long expected = expectedOn(companyId, date, totalEmployees);

        long present = countStatus(attendances, AttendanceStatus.PRESENT);
        long late = countStatus(attendances, AttendanceStatus.LATE);
        long leave = countStatus(attendances, AttendanceStatus.LEAVE)
                + countStatus(attendances, AttendanceStatus.SICK)
                + countStatus(attendances, AttendanceStatus.PERMIT);

        // Absent is derived: rostered minus everyone with any record for the day.
        long absent = Math.max(0, expected - attendances.size());

        return new DailyAttendanceReportResponse(
                date,
                company.getTimezone(),
                new DailyAttendanceReportResponse.Summary(
                        totalEmployees, expected, present, late, absent, leave),
                rows);
    }

    @Transactional(readOnly = true)
    public MonthlyAttendanceReportResponse monthly(Integer requestedYear,
                                                   Integer requestedMonth,
                                                   Long departmentId,
                                                   Long employeeId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        LocalDate today = today(company);

        YearMonth yearMonth = YearMonth.of(
                requestedYear != null ? requestedYear : today.getYear(),
                requestedMonth != null ? requestedMonth : today.getMonthValue());

        if (employeeId != null) {
            employeeService.requireInCompany(employeeId, companyId);
        }

        List<EmployeeAttendanceSummary> rows = attendanceRepository.summariseByEmployee(
                companyId, yearMonth.atDay(1), yearMonth.atEndOfMonth(), departmentId, employeeId);

        return new MonthlyAttendanceReportResponse(
                yearMonth.getYear(),
                yearMonth.getMonthValue(),
                yearMonth.atDay(1),
                yearMonth.atEndOfMonth(),
                company.getTimezone(),
                rows.size(),
                rows);
    }

    @Transactional(readOnly = true)
    public EmployeeAttendanceReportResponse forEmployee(Long employeeId,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        Employee employee = employeeService.requireInCompany(employeeId, companyId);

        LocalDate today = today(company);
        LocalDate from = startDate != null ? startDate : today.withDayOfMonth(1);
        LocalDate to = endDate != null ? endDate : today;
        validateRange(from, to);

        List<Attendance> records =
                attendanceRepository.findForEmployeeReport(companyId, employeeId, from, to);

        EmployeeAttendanceSummary summary = attendanceRepository
                .summariseByEmployee(companyId, from, to, null, employeeId)
                .stream().findFirst()
                .orElseGet(() -> emptySummary(employee));

        ZoneId zone = company.zoneId();
        return new EmployeeAttendanceReportResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                employee.getPosition() == null ? null : employee.getPosition().getName(),
                from,
                to,
                company.getTimezone(),
                summary,
                records.stream().map(record -> attendanceMapper.toResponse(record, zone)).toList());
    }

    private DailyAttendanceReportResponse.Row toRow(Attendance attendance, ZoneId zone) {
        Employee employee = attendance.getEmployee();
        return new DailyAttendanceReportResponse.Row(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                attendance.getShift() == null ? null : attendance.getShift().getName(),
                toLocalTime(attendance.getCheckIn(), zone),
                toLocalTime(attendance.getCheckOut(), zone),
                attendance.getStatus(),
                attendance.getLateMinutes(),
                attendance.getEarlyLeaveMinutes(),
                attendance.getWorkMinutes(),
                attendance.getNotes());
    }

    private EmployeeAttendanceSummary emptySummary(Employee employee) {
        return new EmployeeAttendanceSummary(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private long expectedOn(Long companyId, LocalDate date, long totalEmployees) {
        if (shiftRepository.findFirstByCompany_IdAndDefaultShiftTrue(companyId).isPresent()) {
            return totalEmployees;
        }
        return employeeShiftRepository.countRosteredEmployeesOn(companyId, date);
    }

    private long countStatus(List<Attendance> attendances, AttendanceStatus status) {
        return attendances.stream().filter(attendance -> attendance.getStatus() == status).count();
    }

    private LocalTime toLocalTime(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalTime();
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Tanggal selesai tidak boleh lebih awal dari tanggal mulai");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Rentang laporan maksimal " + MAX_RANGE_DAYS + " hari");
        }
    }

    private LocalDate today(Company company) {
        return clock.instant().atZone(company.zoneId()).toLocalDate();
    }

    private Company loadCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.COMPANY_NOT_FOUND, "Perusahaan tidak ditemukan"));
    }
}
