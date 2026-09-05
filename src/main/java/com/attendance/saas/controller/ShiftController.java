package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.shift.ShiftRequest;
import com.attendance.saas.dto.shift.ShiftResponse;
import com.attendance.saas.service.ShiftService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shifts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Shift")
public class ShiftController {

    private final ShiftService shiftService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Daftar shift dengan pagination dan pencarian")
    public ApiResponse<PageResponse<ShiftResponse>> list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(shiftService.search(keyword, pageable));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Seluruh shift tanpa pagination, untuk dropdown")
    public ApiResponse<List<ShiftResponse>> listAll() {
        return ApiResponse.success(shiftService.listAll());
    }

    @GetMapping("/{shiftId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail shift")
    public ApiResponse<ShiftResponse> getById(@PathVariable Long shiftId) {
        return ApiResponse.success(shiftService.getById(shiftId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Tambah shift",
            description = "Jam selesai lebih awal dari jam mulai berarti shift malam yang berakhir esok hari")
    public ResponseEntity<ApiResponse<ShiftResponse>> create(@Valid @RequestBody ShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(shiftService.create(request), "Shift berhasil dibuat"));
    }

    @PutMapping("/{shiftId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Ubah shift")
    public ApiResponse<ShiftResponse> update(@PathVariable Long shiftId,
                                             @Valid @RequestBody ShiftRequest request) {
        return ApiResponse.success(shiftService.update(shiftId, request), "Shift berhasil diperbarui");
    }

    @DeleteMapping("/{shiftId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Hapus shift (ditolak jika masih dipakai jadwal atau absensi)")
    public ApiResponse<Void> delete(@PathVariable Long shiftId) {
        shiftService.delete(shiftId);
        return ApiResponse.message("Shift berhasil dihapus");
    }
}
