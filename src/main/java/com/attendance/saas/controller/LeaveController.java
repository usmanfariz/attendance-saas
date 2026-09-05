package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.leave.LeaveRequestCreateRequest;
import com.attendance.saas.dto.leave.LeaveRequestResponse;
import com.attendance.saas.dto.leave.LeaveReviewRequest;
import com.attendance.saas.entity.enums.LeaveType;
import com.attendance.saas.entity.enums.RequestStatus;
import com.attendance.saas.service.LeaveService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/leave")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Leave")
public class LeaveController {

    private final LeaveService leaveService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Ajukan cuti, sakit, atau izin")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> create(
            @Valid @RequestBody LeaveRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(leaveService.create(request), "Pengajuan cuti berhasil dibuat"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Daftar pengajuan cuti dengan filter")
    public ApiResponse<PageResponse<LeaveRequestResponse>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) LeaveType leaveType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(leaveService.search(
                employeeId, departmentId, status, leaveType, startDate, endDate, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Pengajuan cuti milik sendiri")
    public ApiResponse<PageResponse<LeaveRequestResponse>> myRequests(
            @RequestParam(required = false) RequestStatus status,
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(leaveService.myRequests(status, pageable));
    }

    @GetMapping("/{leaveId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail pengajuan cuti")
    public ApiResponse<LeaveRequestResponse> getById(@PathVariable Long leaveId) {
        return ApiResponse.success(leaveService.getById(leaveId));
    }

    @PutMapping("/{leaveId}/approve")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Setujui pengajuan cuti",
            description = "Hari yang disetujui langsung ditandai pada absensi agar tidak terhitung ABSENT")
    public ApiResponse<LeaveRequestResponse> approve(@PathVariable Long leaveId,
                                                     @Valid @RequestBody LeaveReviewRequest request) {
        return ApiResponse.success(leaveService.approve(leaveId, request), "Pengajuan cuti disetujui");
    }

    @PutMapping("/{leaveId}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Tolak pengajuan cuti (alasan wajib diisi)")
    public ApiResponse<LeaveRequestResponse> reject(@PathVariable Long leaveId,
                                                    @Valid @RequestBody LeaveReviewRequest request) {
        return ApiResponse.success(leaveService.reject(leaveId, request), "Pengajuan cuti ditolak");
    }

    @PutMapping("/{leaveId}/cancel")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Batalkan pengajuan cuti sendiri yang masih PENDING")
    public ApiResponse<LeaveRequestResponse> cancel(@PathVariable Long leaveId) {
        return ApiResponse.success(leaveService.cancel(leaveId), "Pengajuan cuti dibatalkan");
    }
}
