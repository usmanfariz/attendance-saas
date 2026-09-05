package com.attendance.saas.service;

import com.attendance.saas.dto.attendance.AttendanceResponse;
import com.attendance.saas.dto.attendance.CheckInRequest;
import com.attendance.saas.dto.attendance.CheckOutRequest;
import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.AttendanceMapper;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.security.SecurityUtils;
import com.attendance.saas.util.ShiftWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Check-in and check-out.
 *
 * <p>All business rules from section 24 of the specification live here:
 * no double check-in, no check-out before a check-in, night shifts checking out
 * on the following calendar day, and lateness / early leave measured against
 * the shift's tolerances. Timestamps are compared in the <em>company</em>
 * timezone, never the server's.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    /** How far back check-out looks for an open shift (covers overnight work). */
    private static final int OPEN_ATTENDANCE_LOOKBACK_DAYS = 2;

    private final AttendanceRepository attendanceRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeService employeeService;
    private final ShiftScheduleResolver scheduleResolver;
    private final AttendanceCalculator calculator;
    private final GeofenceService geofenceService;
    private final AttendanceMapper attendanceMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public AttendanceResponse checkIn(CheckInRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        Employee employee = employeeService.requireOwnEmployee();
        assertEmployeeActive(employee);

        // Rejected before anything is written, so a refused check-in leaves no trace.
        geofenceService.assertWithinGeofence(companyId, request.latitude(), request.longitude());

        Instant now = clock.instant();
        ShiftWindow window = scheduleResolver.resolveForCheckIn(company, employee, now);
        LocalDate businessDate = window.businessDate();

        attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(companyId, employee.getId(), businessDate)
                .filter(Attendance::hasCheckedIn)
                .ifPresent(existing -> {
                    throw new ConflictException(
                            ErrorCode.ALREADY_CHECKED_IN,
                            "Anda sudah melakukan check-in untuk shift tanggal " + businessDate);
                });

        int lateMinutes = calculator.lateMinutes(window, now);
        AttendanceStatus status = calculator.statusFor(lateMinutes);

        Attendance attendance = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(companyId, employee.getId(), businessDate)
                .orElseGet(() -> Attendance.builder()
                        .company(company)
                        .employee(employee)
                        .attendanceDate(businessDate)
                        .build());

        attendance.setShift(window.shift());
        attendance.setCheckIn(now);
        attendance.setCheckInLatitude(request.latitude());
        attendance.setCheckInLongitude(request.longitude());
        attendance.setCheckInPhoto(request.photo());
        attendance.setNotes(request.notes());
        attendance.setLateMinutes(lateMinutes);
        attendance.setStatus(status);
        attendanceRepository.save(attendance);

        auditService.record(AuditAction.CHECK_IN, "Attendance", attendance.getId(),
                "Check-in %s, status %s, terlambat %d menit".formatted(businessDate, status, lateMinutes));
        log.info("Employee {} checked in for {} (shift {}, status {}, late {} min)",
                employee.getId(), businessDate, window.shift().getId(), status, lateMinutes);

        return attendanceMapper.toResponse(attendance, company.zoneId());
    }

    @Transactional
    public AttendanceResponse checkOut(CheckOutRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        Employee employee = employeeService.requireOwnEmployee();

        Instant now = clock.instant();
        LocalDate today = now.atZone(company.zoneId()).toLocalDate();

        // A night shift started yesterday is still the open one this morning.
        List<Attendance> open = attendanceRepository.findOpenAttendances(
                companyId,
                employee.getId(),
                today.minusDays(OPEN_ATTENDANCE_LOOKBACK_DAYS),
                today);

        Attendance attendance = open.stream().findFirst()
                .orElseThrow(() -> alreadyCheckedOutOrNeverCheckedIn(companyId, employee.getId(), today));

        if (now.isBefore(attendance.getCheckIn())) {
            throw new BusinessException(
                    ErrorCode.CHECK_OUT_BEFORE_CHECK_IN,
                    "Waktu check-out tidak boleh mendahului check-in");
        }

        attendance.setCheckOut(now);
        attendance.setCheckOutLatitude(request.latitude());
        attendance.setCheckOutLongitude(request.longitude());
        attendance.setCheckOutPhoto(request.photo());
        attendance.setWorkMinutes(calculator.workMinutes(attendance.getCheckIn(), now));

        if (attendance.getShift() != null) {
            ShiftWindow window = scheduleResolver.windowFor(
                    company, attendance.getShift(), attendance.getAttendanceDate());
            attendance.setEarlyLeaveMinutes(calculator.earlyLeaveMinutes(window, now));
        }
        if (request.notes() != null) {
            attendance.setNotes(request.notes());
        }

        auditService.record(AuditAction.CHECK_OUT, "Attendance", attendance.getId(),
                "Check-out %s, kerja %d menit".formatted(
                        attendance.getAttendanceDate(), attendance.getWorkMinutes()));
        log.info("Employee {} checked out for {} (work {} min, early leave {} min)",
                employee.getId(), attendance.getAttendanceDate(),
                attendance.getWorkMinutes(), attendance.getEarlyLeaveMinutes());

        return attendanceMapper.toResponse(attendance, company.zoneId());
    }

    /** Today's record for the current employee, if the day has started. */
    @Transactional(readOnly = true)
    public Optional<AttendanceResponse> myCurrentAttendance() {
        Long companyId = SecurityUtils.requireCompanyId();
        Company company = loadCompany(companyId);
        Employee employee = employeeService.requireOwnEmployee();

        LocalDate today = clock.instant().atZone(company.zoneId()).toLocalDate();

        return attendanceRepository
                .findOpenAttendances(companyId, employee.getId(), today.minusDays(1), today)
                .stream()
                .findFirst()
                .or(() -> attendanceRepository.findByCompany_IdAndEmployee_IdAndAttendanceDate(
                        companyId, employee.getId(), today))
                .map(attendance -> attendanceMapper.toResponse(attendance, company.zoneId()));
    }

    private BusinessException alreadyCheckedOutOrNeverCheckedIn(Long companyId,
                                                                Long employeeId,
                                                                LocalDate today) {
        boolean closedToday = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(companyId, employeeId, today)
                .map(Attendance::hasCheckedOut)
                .orElse(false);

        if (closedToday) {
            return new BusinessException(
                    ErrorCode.ALREADY_CHECKED_OUT, "Anda sudah melakukan check-out hari ini");
        }
        return new BusinessException(
                ErrorCode.NOT_CHECKED_IN, "Anda belum melakukan check-in");
    }

    private void assertEmployeeActive(Employee employee) {
        if (!employee.isActive()) {
            throw new BusinessException(
                    ErrorCode.EMPLOYEE_INACTIVE,
                    "Status karyawan tidak aktif sehingga tidak dapat melakukan absensi");
        }
    }

    private Company loadCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.COMPANY_NOT_FOUND, "Perusahaan tidak ditemukan"));
    }
}
