package com.attendance.saas.dto.billing;

import com.attendance.saas.entity.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Schema(name = "InvoiceResponse")
public record InvoiceResponse(
        Long id,
        Long companyId,
        String companyName,
        String invoiceNumber,
        String planCode,
        String planName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        String currency,
        InvoiceStatus status,
        Instant issuedAt,
        LocalDate dueDate,
        Instant paidAt,
        String paymentReference
) {
}
