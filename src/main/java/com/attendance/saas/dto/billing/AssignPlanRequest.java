package com.attendance.saas.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AssignPlanRequest")
public record AssignPlanRequest(

        @NotNull(message = "Plan id wajib diisi")
        Long planId,

        @Min(value = 0, message = "Masa trial tidak boleh negatif")
        @Max(value = 365, message = "Masa trial maksimal 365 hari")
        @Schema(description = "Hari masa percobaan; 0 atau kosong berarti langsung aktif",
                example = "14")
        Integer trialDays,

        @Schema(defaultValue = "true")
        Boolean autoRenew
) {
}
