package com.attendance.saas.service;

import com.attendance.saas.dto.dashboard.CompanyDashboardResponse;
import com.attendance.saas.dto.dashboard.EmployeeDashboardResponse;
import com.attendance.saas.dto.report.AttendanceStatusCount;
import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.CompanySettings;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.LeaveType;
import com.attendance.saas.entity.enums.RequestStatus;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.AttendanceMapper;
import com.attendance.saas.mapper.ShiftMapper;
import com.attendance.saas.repository.AttendanceCorrectionRepository;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.CompanySettingsRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.EmployeeShiftRepository;
import com.attendance.saas.repository.LeaveRequestRepository;
import com.attendance.saas.repository.ShiftRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregates for the company and employee dashboards.
 *
 * <p>"Absent" is derived rather than stored: nothing writes an {@code ABSENT}
 * row today, so it is computed as the employees rostered for the date minus
 * those who have any attendance record for it. Measuring against the roster
 * rather than the headcount keeps people with no shift out of the count.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeShiftRepository employeeShiftRepository;
    private final ShiftRepository shiftRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EmployeeService employeeService;
    private final ShiftScheduleResolver scheduleResolver;
    private final AttendanceMapper attendanceMapper;
    private final ShiftMapper shiftMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CompanyDashboardResponse companyDashboard(LocalDate requestedDate) {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        LocalDate date = requestedDate != null ? requestedDate : today(company);

        Map<AttendanceStatus, Long> counts = statusCounts(
                attendanceRepository.countByStatusOn(companyId, date));

        long totalEmployees = employeeRepository.countByCompany_IdAndStatus(
                companyId, EmployeeStatus.ACTIVE);
        long present = counts.getOrDefault(AttendanceStatus.PRESENT, 0L);
        long late = counts.getOrDefault(AttendanceStatus.LATE, 0L);
        long leave = leaveStatusTotal(counts);
        long recorded = counts.values().stream().mapToLong(Long::longValue).sum();

        long expected = expectedToday(companyId, date, totalEmployees);
        long absent = Math.max(0, expected - recorded);

        return new CompanyDashboardResponse(
                date,
                company.getTimezone(),
                totalEmployees,
                expected,
                present,
                late,
                absent,
                leave,
                leaveRequestRepository.countByCompany_IdAndStatus(companyId, RequestStatus.PENDING),
                correctionRepository.countByCompany_IdAndStatus(companyId, RequestStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse employeeDashboard() {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        Employee employee = employeeService.requireOwnEmployee();

        LocalDate date = today(company);
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = date.withDayOfMonth(date.lengthOfMonth());

        Attendance todayAttendance = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(companyId, employee.getId(), date)
                .orElseGet(() -> attendanceRepository
                        .findOpenAttendances(companyId, employee.getId(), date.minusDays(1), date)
                        .stream().findFirst().orElse(null));

        var shift = scheduleResolver.resolveForDate(company, employee, date);

        return new EmployeeDashboardResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                date,
                company.getTimezone(),
                todayAttendance == null
                        ? null : attendanceMapper.toResponse(todayAttendance, company.zoneId()),
                shift.map(window -> shiftMapper.toResponse(window.shift())).orElse(null),
                monthlySummary(companyId, employee.getId(), monthStart, monthEnd),
                leaveBalance(companyId, employee.getId(), date));
    }

    private EmployeeDashboardResponse.MonthlyAttendanceSummary monthlySummary(Long companyId,
                                                                             Long employeeId,
                                                                             LocalDate monthStart,
                                                                             LocalDate monthEnd) {
        Map<AttendanceStatus, Long> counts = statusCounts(
                attendanceRepository.countByStatusForEmployee(companyId, employeeId, monthStart, monthEnd));

        Object[] sums = attendanceRepository
                .sumMinutesForEmployee(companyId, employeeId, monthStart, monthEnd)
                .stream().findFirst().orElse(new Object[]{0L, 0L});

        return new EmployeeDashboardResponse.MonthlyAttendanceSummary(
                monthStart,
                monthEnd,
                counts.getOrDefault(AttendanceStatus.PRESENT, 0L),
                counts.getOrDefault(AttendanceStatus.LATE, 0L),
                leaveStatusTotal(counts),
                counts.getOrDefault(AttendanceStatus.ABSENT, 0L),
                toLong(sums[0]),
                toLong(sums[1]));
    }

    /**
     * Annual leave only: sick days and personal permission do not draw down the
     * yearly allowance.
     */
    private EmployeeDashboardResponse.LeaveBalance leaveBalance(Long companyId,
                                                                Long employeeId,
                                                                LocalDate date) {
        int year = date.getYear();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        int quota = companySettingsRepository.findByCompany_Id(companyId)
                .map(CompanySettings::getAnnualLeaveQuotaDays)
                .orElse(12);

        int used = (int) leaveRequestRepository.sumDaysByTypeAndStatus(
                companyId, employeeId, LeaveType.CUTI, RequestStatus.APPROVED, yearStart, yearEnd);
        int pending = (int) leaveRequestRepository.sumDaysByTypeAndStatus(
                companyId, employeeId, LeaveType.CUTI, RequestStatus.PENDING, yearStart, yearEnd);

        return new EmployeeDashboardResponse.LeaveBalance(
                year, quota, used, Math.max(0, quota - used), pending);
    }

    /**
     * Employees expected to work on the date. With a company-wide default shift
     * everybody is expected; otherwise only those with a roster entry.
     */
    private long expectedToday(Long companyId, LocalDate date, long totalEmployees) {
        if (shiftRepository.findFirstByCompany_IdAndDefaultShiftTrue(companyId).isPresent()) {
            return totalEmployees;
        }
        return employeeShiftRepository.countRosteredEmployeesOn(companyId, date);
    }

    private Map<AttendanceStatus, Long> statusCounts(List<AttendanceStatusCount> rows) {
        Map<AttendanceStatus, Long> counts = new EnumMap<>(AttendanceStatus.class);
        rows.forEach(row -> counts.put(row.status(), row.count()));
        return counts;
    }

    private long leaveStatusTotal(Map<AttendanceStatus, Long> counts) {
        return counts.getOrDefault(AttendanceStatus.LEAVE, 0L)
                + counts.getOrDefault(AttendanceStatus.SICK, 0L)
                + counts.getOrDefault(AttendanceStatus.PERMIT, 0L);
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
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
