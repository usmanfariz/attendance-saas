package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.company.CompanyResponse;
import com.attendance.saas.dto.company.CompanyStatusUpdateRequest;
import com.attendance.saas.dto.company.CompanyUpdateRequest;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Company")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Daftar seluruh perusahaan (super admin)")
    public ApiResponse<PageResponse<CompanyResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CompanyStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(companyService.search(keyword, status, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Profil perusahaan milik user yang sedang login")
    public ApiResponse<CompanyResponse> currentCompany() {
        return ApiResponse.success(companyService.getCurrentCompany());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Ubah profil perusahaan sendiri")
    public ApiResponse<CompanyResponse> updateCurrentCompany(
            @Valid @RequestBody CompanyUpdateRequest request) {
        return ApiResponse.success(
                companyService.updateCurrentCompany(request), "Data perusahaan berhasil diperbarui");
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    @Operation(summary = "Detail perusahaan berdasarkan id")
    public ApiResponse<CompanyResponse> getById(@PathVariable Long companyId) {
        return ApiResponse.success(companyService.getById(companyId));
    }

    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    @Operation(summary = "Ubah data perusahaan berdasarkan id")
    public ApiResponse<CompanyResponse> update(@PathVariable Long companyId,
                                               @Valid @RequestBody CompanyUpdateRequest request) {
        return ApiResponse.success(
                companyService.update(companyId, request), "Data perusahaan berhasil diperbarui");
    }

    @PatchMapping("/{companyId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Aktifkan atau nonaktifkan perusahaan (super admin)")
    public ApiResponse<CompanyResponse> updateStatus(@PathVariable Long companyId,
                                                     @Valid @RequestBody CompanyStatusUpdateRequest request) {
        return ApiResponse.success(
                companyService.updateStatus(companyId, request), "Status perusahaan berhasil diperbarui");
    }
}
