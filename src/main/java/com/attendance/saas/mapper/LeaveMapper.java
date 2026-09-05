package com.attendance.saas.mapper;

import com.attendance.saas.dto.leave.LeaveRequestResponse;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.LeaveRequest;
import com.attendance.saas.entity.User;
import org.springframework.stereotype.Component;

@Component
public class LeaveMapper {

    public LeaveRequestResponse toResponse(LeaveRequest request) {
        Employee employee = request.getEmployee();
        User reviewer = request.getReviewedBy();

        return new LeaveRequestResponse(
                request.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                request.getLeaveType(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTotalDays(),
                request.getReason(),
                request.getStatus(),
                reviewer == null ? null : reviewer.getId(),
                reviewer == null ? null : reviewer.getName(),
                request.getReviewedAt(),
                request.getReviewNote(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}
