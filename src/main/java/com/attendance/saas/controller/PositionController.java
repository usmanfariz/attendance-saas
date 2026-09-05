package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.position.PositionRequest;
import com.attendance.saas.dto.position.PositionResponse;
import com.attendance.saas.service.PositionService;
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
@RequestMapping("/api/v1/positions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Position")
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Daftar posisi dengan pagination dan pencarian")
    public ApiResponse<PageResponse<PositionResponse>> list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(positionService.search(keyword, pageable));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Seluruh posisi tanpa pagination, untuk dropdown")
    public ApiResponse<List<PositionResponse>> listAll() {
        return ApiResponse.success(positionService.listAll());
    }

    @GetMapping("/{positionId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail posisi")
    public ApiResponse<PositionResponse> getById(@PathVariable Long positionId) {
        return ApiResponse.success(positionService.getById(positionId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Tambah posisi")
    public ResponseEntity<ApiResponse<PositionResponse>> create(@Valid @RequestBody PositionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(positionService.create(request), "Posisi berhasil dibuat"));
    }

    @PutMapping("/{positionId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Ubah posisi")
    public ApiResponse<PositionResponse> update(@PathVariable Long positionId,
                                                @Valid @RequestBody PositionRequest request) {
        return ApiResponse.success(positionService.update(positionId, request), "Posisi berhasil diperbarui");
    }

    @DeleteMapping("/{positionId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Hapus posisi (ditolak jika masih dipakai karyawan)")
    public ApiResponse<Void> delete(@PathVariable Long positionId) {
        positionService.delete(positionId);
        return ApiResponse.message("Posisi berhasil dihapus");
    }
}
