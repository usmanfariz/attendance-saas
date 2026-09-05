package com.attendance.saas.controller;

import com.attendance.saas.dto.billing.InvoiceResponse;
import com.attendance.saas.dto.billing.MarkInvoicePaidRequest;
import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.enums.InvoiceStatus;
import com.attendance.saas.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Invoice")
public class InvoiceController {

    private final BillingService billingService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Daftar tagihan seluruh perusahaan")
    public ApiResponse<PageResponse<InvoiceResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) InvoiceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(billingService.searchAll(companyId, status, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Tagihan perusahaan sendiri")
    public ApiResponse<PageResponse<InvoiceResponse>> myInvoices(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(billingService.myInvoices(pageable));
    }

    @PutMapping("/{invoiceId}/pay")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tandai tagihan lunas",
            description = """
                    Tidak ada integrasi payment gateway. Endpoint inilah titik sambung
                    yang akan dipanggil webhook penyedia pembayaran bila nanti dipasang.
                    Melunasi tagihan mengembalikan langganan PAST_DUE menjadi ACTIVE.
                    """)
    public ApiResponse<InvoiceResponse> markPaid(@PathVariable Long invoiceId,
                                                 @Valid @RequestBody MarkInvoicePaidRequest request) {
        return ApiResponse.success(billingService.markPaid(invoiceId, request), "Tagihan ditandai lunas");
    }

    @PutMapping("/{invoiceId}/void")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Batalkan tagihan yang terbit karena kekeliruan")
    public ApiResponse<InvoiceResponse> voidInvoice(@PathVariable Long invoiceId) {
        return ApiResponse.success(billingService.voidInvoice(invoiceId), "Tagihan dibatalkan");
    }

    @PostMapping("/billing-cycle/run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Jalankan siklus penagihan",
            description = """
                    Menandai tagihan yang lewat jatuh tempo sebagai OVERDUE, memindahkan
                    langganannya ke PAST_DUE, lalu memperpanjang langganan yang periodenya
                    berakhir dan menerbitkan tagihan berikutnya. Idempoten, aman dijalankan
                    berulang; ditujukan untuk dipanggil scheduler harian.
                    """)
    public ApiResponse<Map<String, Integer>> runBillingCycle() {
        int renewed = billingService.runBillingCycle();
        return ApiResponse.success(Map.of("renewedSubscriptions", renewed), "Siklus penagihan selesai");
    }
}
