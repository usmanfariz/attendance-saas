package com.attendance.saas.controller;

import com.attendance.saas.dto.billing.PlanRequest;
import com.attendance.saas.dto.billing.PlanResponse;
import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Plan")
public class PlanController {

    private final PlanService planService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Seluruh plan termasuk yang sudah dinonaktifkan")
    public ApiResponse<List<PlanResponse>> list() {
        return ApiResponse.success(planService.listAll());
    }

    @GetMapping("/available")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    @Operation(summary = "Plan aktif yang dapat dipilih perusahaan")
    public ApiResponse<List<PlanResponse>> available() {
        return ApiResponse.success(planService.listAvailable());
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Detail plan")
    public ApiResponse<PlanResponse> getById(@PathVariable Long planId) {
        return ApiResponse.success(planService.getById(planId));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tambah plan",
            description = "Batas yang dikosongkan berarti tanpa batas")
    public ResponseEntity<ApiResponse<PlanResponse>> create(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(planService.create(request), "Plan berhasil dibuat"));
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Ubah plan",
            description = "Perubahan harga hanya berlaku untuk tagihan berikutnya")
    public ApiResponse<PlanResponse> update(@PathVariable Long planId,
                                            @Valid @RequestBody PlanRequest request) {
        return ApiResponse.success(planService.update(planId, request), "Plan berhasil diperbarui");
    }

    @DeleteMapping("/{planId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Hapus plan (ditolak jika masih dipakai perusahaan)")
    public ApiResponse<Void> delete(@PathVariable Long planId) {
        planService.delete(planId);
        return ApiResponse.message("Plan berhasil dihapus");
    }
}
