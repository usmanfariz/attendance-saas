package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.employee.CreateEmployeeAccountRequest;
import com.attendance.saas.dto.employee.EmployeeAccountResponse;
import com.attendance.saas.dto.employee.EmployeeCreateRequest;
import com.attendance.saas.dto.employee.EmployeeResponse;
import com.attendance.saas.dto.employee.EmployeeUpdateRequest;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.service.EmployeeAccountService;
import com.attendance.saas.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Employee")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeAccountService employeeAccountService;

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Daftar karyawan dengan pagination, pencarian, dan filter")
    public ApiResponse<PageResponse<EmployeeResponse>> list(
            @Parameter(description = "Cari pada kode, nama, email, atau telepon")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long positionId,
            @RequestParam(required = false) EmployeeStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(
                employeeService.search(keyword, departmentId, positionId, status, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Data karyawan milik akun yang sedang login")
    public ApiResponse<EmployeeResponse> myProfile() {
        return ApiResponse.success(employeeService.getOwnProfile());
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail karyawan")
    public ApiResponse<EmployeeResponse> getById(@PathVariable Long employeeId) {
        return ApiResponse.success(employeeService.getById(employeeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Tambah karyawan")
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(
            @Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(employeeService.create(request), "Karyawan berhasil dibuat"));
    }

    @PutMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Ubah data karyawan (kode karyawan tidak dapat diubah)")
    public ApiResponse<EmployeeResponse> update(@PathVariable Long employeeId,
                                                @Valid @RequestBody EmployeeUpdateRequest request) {
        return ApiResponse.success(
                employeeService.update(employeeId, request), "Data karyawan berhasil diperbarui");
    }

    @DeleteMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Nonaktifkan karyawan (soft delete, status menjadi RESIGNED)")
    public ApiResponse<EmployeeResponse> deactivate(@PathVariable Long employeeId) {
        return ApiResponse.success(
                employeeService.deactivate(employeeId), "Karyawan berhasil dinonaktifkan");
    }

    @PostMapping("/{employeeId}/account")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Buatkan akun login untuk karyawan")
    public ResponseEntity<ApiResponse<EmployeeAccountResponse>> createAccount(
            @PathVariable Long employeeId,
            @Valid @RequestBody CreateEmployeeAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        employeeAccountService.createAccount(employeeId, request),
                        "Akun karyawan berhasil dibuat"));
    }
}
