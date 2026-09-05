package com.attendance.saas.mapper;

import com.attendance.saas.dto.correction.AttendanceCorrectionResponse;
import com.attendance.saas.entity.AttendanceCorrection;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AttendanceCorrectionMapper {

    public AttendanceCorrectionResponse toResponse(AttendanceCorrection correction) {
        Employee employee = correction.getEmployee();
        User reviewer = correction.getReviewedBy();

        return new AttendanceCorrectionResponse(
                correction.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                correction.getAttendance() == null ? null : correction.getAttendance().getId(),
                correction.getAttendanceDate(),
                correction.getRequestedCheckIn(),
                correction.getRequestedCheckOut(),
                correction.getReason(),
                correction.getStatus(),
                reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : reviewer.getName(),
                correction.getReviewedAt(),
                correction.getReviewNote(),
                correction.getPreviousCheckIn(),
                correction.getPreviousCheckOut(),
                correction.getCreatedAt(),
                correction.getUpdatedAt());
    }
}
