package com.attendance.saas.mapper;

import com.attendance.saas.dto.department.DepartmentResponse;
import com.attendance.saas.entity.Department;
import org.springframework.stereotype.Component;

@Component
public class DepartmentMapper {

    public DepartmentResponse toResponse(Department department, long employeeCount) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getDescription(),
                employeeCount,
                department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
