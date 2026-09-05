package com.attendance.saas.dto.company;

import com.attendance.saas.entity.enums.CompanyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CompanyStatusUpdateRequest")
public record CompanyStatusUpdateRequest(

        @NotNull(message = "Status wajib diisi")
        CompanyStatus status
) {
}
