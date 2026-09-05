package com.attendance.saas.controller;

import com.attendance.saas.dto.billing.AssignPlanRequest;
import com.attendance.saas.dto.billing.SubscriptionResponse;
import com.attendance.saas.dto.billing.SubscriptionUsageResponse;
import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.service.SubscriptionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Daftar langganan seluruh perusahaan")
    public ApiResponse<PageResponse<SubscriptionResponse>> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long planId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(subscriptionService.search(status, planId, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Langganan perusahaan sendiri beserta pemakaian kuotanya")
    public ApiResponse<SubscriptionUsageResponse> mySubscription() {
        return ApiResponse.success(subscriptionService.getOwnSubscriptionWithUsage());
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    @Operation(summary = "Langganan satu perusahaan")
    public ApiResponse<SubscriptionResponse> forCompany(@PathVariable Long companyId) {
        return ApiResponse.success(subscriptionService.getForCompany(companyId));
    }

    @PutMapping("/company/{companyId}/plan")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Pindahkan perusahaan ke plan lain",
            description = """
                    Berlaku seketika dan memulai periode tagihan baru. Data yang sudah ada
                    tidak pernah dihapus meski plan barunya lebih kecil; batas hanya
                    menghentikan penambahan data baru.
                    """)
    public ApiResponse<SubscriptionResponse> assignPlan(@PathVariable Long companyId,
                                                        @Valid @RequestBody AssignPlanRequest request) {
        return ApiResponse.success(
                subscriptionService.assignPlan(companyId, request), "Plan perusahaan berhasil diubah");
    }

    @PutMapping("/company/{companyId}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Batalkan langganan",
            description = "Perusahaan tetap dapat memakai layanan sampai periode berjalan berakhir")
    public ApiResponse<SubscriptionResponse> cancel(@PathVariable Long companyId) {
        return ApiResponse.success(subscriptionService.cancel(companyId), "Langganan dibatalkan");
    }

    @PutMapping("/company/{companyId}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Aktifkan kembali langganan yang dibatalkan atau kedaluwarsa")
    public ApiResponse<SubscriptionResponse> reactivate(@PathVariable Long companyId) {
        return ApiResponse.success(
                subscriptionService.reactivate(companyId), "Langganan diaktifkan kembali");
    }
}
