package com.attendance.saas.mapper;

import com.attendance.saas.dto.company.CompanyResponse;
import com.attendance.saas.entity.Company;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {

    public CompanyResponse toResponse(Company company) {
        if (company == null) {
            return null;
        }
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getCode(),
                company.getEmail(),
                company.getPhone(),
                company.getAddress(),
                company.getTimezone(),
                company.getStatus(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }
}
