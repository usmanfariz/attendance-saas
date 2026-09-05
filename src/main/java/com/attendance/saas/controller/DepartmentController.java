package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.department.DepartmentRequest;
import com.attendance.saas.dto.department.DepartmentResponse;
import com.attendance.saas.service.DepartmentService;
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
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Department")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Daftar departemen dengan pagination dan pencarian")
    public ApiResponse<PageResponse<DepartmentResponse>> list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(departmentService.search(keyword, pageable));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Seluruh departemen tanpa pagination, untuk dropdown")
    public ApiResponse<List<DepartmentResponse>> listAll() {
        return ApiResponse.success(departmentService.listAll());
    }

    @GetMapping("/{departmentId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail departemen")
    public ApiResponse<DepartmentResponse> getById(@PathVariable Long departmentId) {
        return ApiResponse.success(departmentService.getById(departmentId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Tambah departemen")
    public ResponseEntity<ApiResponse<DepartmentResponse>> create(
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(departmentService.create(request), "Departemen berhasil dibuat"));
    }

    @PutMapping("/{departmentId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Ubah departemen")
    public ApiResponse<DepartmentResponse> update(@PathVariable Long departmentId,
                                                  @Valid @RequestBody DepartmentRequest request) {
        return ApiResponse.success(
                departmentService.update(departmentId, request), "Departemen berhasil diperbarui");
    }

    @DeleteMapping("/{departmentId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @Operation(summary = "Hapus departemen (ditolak jika masih dipakai karyawan)")
    public ApiResponse<Void> delete(@PathVariable Long departmentId) {
        departmentService.delete(departmentId);
        return ApiResponse.message("Departemen berhasil dihapus");
    }
}
