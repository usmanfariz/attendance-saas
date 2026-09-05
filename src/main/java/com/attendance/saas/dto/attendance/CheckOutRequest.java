package com.attendance.saas.dto.attendance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CheckOutRequest")
public record CheckOutRequest(

        @DecimalMin(value = "-90.0", message = "Latitude tidak valid")
        @DecimalMax(value = "90.0", message = "Latitude tidak valid")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude tidak valid")
        @DecimalMax(value = "180.0", message = "Longitude tidak valid")
        BigDecimal longitude,

        @Size(max = 500)
        String photo,

        @Size(max = 500)
        String notes
) {
}
