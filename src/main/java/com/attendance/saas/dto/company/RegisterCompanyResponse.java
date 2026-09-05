package com.attendance.saas.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RegisterCompanyResponse")
public record RegisterCompanyResponse(
        CompanyResponse company,
        Long adminUserId,
        String adminEmail
) {
}
