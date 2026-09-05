package com.attendance.saas.dto.company;

import com.attendance.saas.entity.enums.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "CompanyResponse")
public record CompanyResponse(
        Long id,
        String name,
        String code,
        String email,
        String phone,
        String address,
        String timezone,
        CompanyStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
