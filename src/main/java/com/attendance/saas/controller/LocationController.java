package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.location.CompanySettingsRequest;
import com.attendance.saas.dto.location.CompanySettingsResponse;
import com.attendance.saas.dto.location.LocationRequest;
import com.attendance.saas.dto.location.LocationResponse;
import com.attendance.saas.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Location")
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Daftar lokasi kantor")
    public ApiResponse<PageResponse<LocationResponse>> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(locationService.list(pageable));
    }

    @GetMapping("/{locationId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Detail lokasi kantor")
    public ApiResponse<LocationResponse> getById(@PathVariable Long locationId) {
        return ApiResponse.success(locationService.getById(locationId));
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Tambah lokasi kantor beserta radius yang diizinkan")
    public ResponseEntity<ApiResponse<LocationResponse>> create(@Valid @RequestBody LocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(locationService.create(request), "Lokasi berhasil dibuat"));
    }

    @PutMapping("/{locationId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Ubah lokasi kantor")
    public ApiResponse<LocationResponse> update(@PathVariable Long locationId,
                                                @Valid @RequestBody LocationRequest request) {
        return ApiResponse.success(locationService.update(locationId, request), "Lokasi berhasil diperbarui");
    }

    @DeleteMapping("/{locationId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Hapus lokasi kantor")
    public ApiResponse<Void> delete(@PathVariable Long locationId) {
        locationService.delete(locationId);
        return ApiResponse.message("Lokasi berhasil dihapus");
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Pengaturan geofencing perusahaan")
    public ApiResponse<CompanySettingsResponse> settings() {
        return ApiResponse.success(locationService.getSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Aktifkan atau nonaktifkan validasi lokasi saat check-in")
    public ApiResponse<CompanySettingsResponse> updateSettings(
            @Valid @RequestBody CompanySettingsRequest request) {
        return ApiResponse.success(
                locationService.updateSettings(request), "Pengaturan berhasil diperbarui");
    }
}
