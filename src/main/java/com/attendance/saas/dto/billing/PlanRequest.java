package com.attendance.saas.dto.billing;

import com.attendance.saas.entity.enums.BillingPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Limits left null mean unlimited.
 */
@Schema(name = "PlanRequest")
public record PlanRequest(

        @NotBlank(message = "Kode plan wajib diisi")
        @Size(max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$",
                message = "Kode plan hanya boleh huruf, angka, garis bawah, dan tanda hubung")
        @Schema(example = "BASIC")
        String code,

        @NotBlank(message = "Nama plan wajib diisi")
        @Size(max = 100)
        @Schema(example = "Basic")
        String name,

        @Size(max = 255)
        String description,

        @NotNull(message = "Harga wajib diisi")
        @DecimalMin(value = "0.0", message = "Harga tidak boleh negatif")
        @Schema(example = "299000.00")
        BigDecimal priceAmount,

        @Size(min = 3, max = 3)
        @Schema(example = "IDR", defaultValue = "IDR")
        String currency,

        @NotNull(message = "Periode penagihan wajib diisi")
        @Schema(example = "MONTHLY")
        BillingPeriod billingPeriod,

        @Min(value = 1, message = "Batas karyawan minimal 1")
        @Schema(example = "50", description = "Kosongkan untuk tanpa batas")
        Integer maxEmployees,

        @Min(value = 1, message = "Batas lokasi minimal 1")
        @Schema(example = "3", description = "Kosongkan untuk tanpa batas")
        Integer maxLocations,

        @Min(value = 1, message = "Batas pengguna minimal 1")
        @Schema(example = "75", description = "Kosongkan untuk tanpa batas")
        Integer maxUsers,

        @Schema(description = "Apakah validasi lokasi termasuk dalam plan", defaultValue = "false")
        Boolean geofenceIncluded,

        @Schema(defaultValue = "true")
        Boolean active,

        @Min(0)
        Integer sortOrder
) {
}
