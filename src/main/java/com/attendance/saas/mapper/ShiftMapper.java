package com.attendance.saas.mapper;

import com.attendance.saas.dto.shift.EmployeeShiftResponse;
import com.attendance.saas.dto.shift.ShiftResponse;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.EmployeeShift;
import com.attendance.saas.entity.Shift;
import org.springframework.stereotype.Component;

@Component
public class ShiftMapper {

    public ShiftResponse toResponse(Shift shift) {
        return new ShiftResponse(
                shift.getId(),
                shift.getName(),
                shift.getStartTime(),
                shift.getEndTime(),
                shift.getLateToleranceMinutes(),
                shift.getEarlyLeaveToleranceMinutes(),
                shift.isDefaultShift(),
                shift.crossesMidnight(),
                shift.scheduledMinutes(),
                shift.getCreatedAt(),
                shift.getUpdatedAt());
    }

    public EmployeeShiftResponse toResponse(EmployeeShift assignment) {
        Employee employee = assignment.getEmployee();
        Shift shift = assignment.getShift();

        return new EmployeeShiftResponse(
                assignment.getId(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                shift.getId(),
                shift.getName(),
                shift.getStartTime(),
                shift.getEndTime(),
                assignment.getShiftDate());
    }
}
