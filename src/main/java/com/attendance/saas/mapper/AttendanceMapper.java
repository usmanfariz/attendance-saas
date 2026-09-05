package com.attendance.saas.mapper;

import com.attendance.saas.dto.attendance.AttendanceResponse;
import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class AttendanceMapper {

    /**
     * @param zone company timezone; check-in and check-out are also rendered as
     *             local wall-clock times so mobile clients do not re-implement
     *             the conversion.
     */
    public AttendanceResponse toResponse(Attendance attendance, ZoneId zone) {
        Employee employee = attendance.getEmployee();
        Shift shift = attendance.getShift();

        return new AttendanceResponse(
                attendance.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                shift == null ? null : shift.getId(),
                shift == null ? null : shift.getName(),
                shift == null ? null : shift.getStartTime(),
                shift == null ? null : shift.getEndTime(),
                attendance.getAttendanceDate(),
                attendance.getCheckIn(),
                attendance.getCheckOut(),
                toLocalTime(attendance.getCheckIn(), zone),
                toLocalTime(attendance.getCheckOut(), zone),
                zone.getId(),
                attendance.getStatus(),
                attendance.getLateMinutes(),
                attendance.getEarlyLeaveMinutes(),
                attendance.getWorkMinutes(),
                attendance.getNotes(),
                attendance.getCreatedAt(),
                attendance.getUpdatedAt());
    }

    private LocalTime toLocalTime(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalTime();
    }
}
