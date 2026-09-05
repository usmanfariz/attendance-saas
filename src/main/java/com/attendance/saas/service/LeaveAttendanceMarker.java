package com.attendance.saas.service;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.LeaveRequest;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Writes an attendance row for every day an approved leave covers, so a day off
 * is never counted as ABSENT (specification section 24).
 *
 * <p>A day the employee actually worked is left untouched: a real check-in
 * outranks the leave that was filed for it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveAttendanceMarker {

    private final AttendanceRepository attendanceRepository;

    @Transactional
    public int markApproved(Company company, LeaveRequest leaveRequest) {
        AttendanceStatus status = leaveRequest.getLeaveType().attendanceStatus();
        Employee employee = leaveRequest.getEmployee();
        int marked = 0;

        for (LocalDate date = leaveRequest.getStartDate();
             !date.isAfter(leaveRequest.getEndDate());
             date = date.plusDays(1)) {

            LocalDate current = date;
            Attendance attendance = attendanceRepository
                    .findByCompany_IdAndEmployee_IdAndAttendanceDate(
                            company.getId(), employee.getId(), current)
                    .orElseGet(() -> Attendance.builder()
                            .company(company)
                            .employee(employee)
                            .attendanceDate(current)
                            .status(status)
                            .build());

            if (attendance.hasCheckedIn()) {
                // They turned up anyway; the recorded work wins over the leave.
                continue;
            }

            attendance.setStatus(status);
            attendance.setNotes(noteFor(leaveRequest));
            attendanceRepository.save(attendance);
            marked++;
        }

        log.info("Leave {} marked {} attendance day(s) as {}", leaveRequest.getId(), marked, status);
        return marked;
    }

    /**
     * Clears the days a previously approved leave had marked, used when an
     * approval is withdrawn. Days with a real check-in are left alone.
     */
    @Transactional
    public int unmark(Company company, LeaveRequest leaveRequest) {
        Employee employee = leaveRequest.getEmployee();
        AttendanceStatus status = leaveRequest.getLeaveType().attendanceStatus();
        int cleared = 0;

        for (LocalDate date = leaveRequest.getStartDate();
             !date.isAfter(leaveRequest.getEndDate());
             date = date.plusDays(1)) {

            var existing = attendanceRepository.findByCompany_IdAndEmployee_IdAndAttendanceDate(
                    company.getId(), employee.getId(), date);

            if (existing.isPresent()
                    && !existing.get().hasCheckedIn()
                    && existing.get().getStatus() == status) {
                attendanceRepository.delete(existing.get());
                cleared++;
            }
        }
        return cleared;
    }

    private String noteFor(LeaveRequest leaveRequest) {
        return "%s: %s".formatted(leaveRequest.getLeaveType(), leaveRequest.getReason());
    }
}
