package com.attendance.saas.mapper;

import com.attendance.saas.dto.employee.EmployeeResponse;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Position;
import org.springframework.stereotype.Component;

@Component
public class EmployeeMapper {

    public EmployeeResponse toResponse(Employee employee, boolean hasUserAccount) {
        Department department = employee.getDepartment();
        Position position = employee.getPosition();

        return new EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getName(),
                employee.getEmail(),
                employee.getPhone(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                position == null ? null : position.getId(),
                position == null ? null : position.getName(),
                employee.getJoinDate(),
                employee.getStatus(),
                hasUserAccount,
                employee.getCreatedAt(),
                employee.getUpdatedAt());
    }
}
