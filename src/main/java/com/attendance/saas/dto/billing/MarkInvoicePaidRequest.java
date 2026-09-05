package com.attendance.saas.dto.billing;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Settles an invoice. No payment gateway is integrated: the reference is
 * whatever identifier the operator got from the channel that actually took the
 * money (bank transfer, gateway callback, manual receipt).
 */
@Schema(name = "MarkInvoicePaidRequest")
public record MarkInvoicePaidRequest(

        @Size(max = 100)
        @Schema(example = "TRF-20260907-0012")
        String paymentReference
) {
}
