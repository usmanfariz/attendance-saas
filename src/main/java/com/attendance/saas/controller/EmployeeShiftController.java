package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.shift.AssignShiftRequest;
import com.attendance.saas.dto.shift.EmployeeShiftResponse;
import com.attendance.saas.service.EmployeeShiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/employee-shifts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Employee Shift")
public class EmployeeShiftController {

    private final EmployeeShiftService employeeShiftService;

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Tetapkan shift untuk seorang karyawan pada rentang tanggal")
    public ResponseEntity<ApiResponse<List<EmployeeShiftResponse>>> assign(
            @Valid @RequestBody AssignShiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        employeeShiftService.assign(request), "Jadwal shift berhasil disimpan"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Daftar jadwal shift pada rentang tanggal")
    public ApiResponse<PageResponse<EmployeeShiftResponse>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 50, sort = "shiftDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(
                employeeShiftService.search(employeeId, startDate, endDate, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Jadwal shift milik karyawan yang sedang login")
    public ApiResponse<List<EmployeeShiftResponse>> myRoster(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(employeeShiftService.myRoster(startDate, endDate));
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR')")
    @Operation(summary = "Hapus satu jadwal shift")
    public ApiResponse<Void> delete(@PathVariable Long assignmentId) {
        employeeShiftService.delete(assignmentId);
        return ApiResponse.message("Jadwal shift berhasil dihapus");
    }
}
