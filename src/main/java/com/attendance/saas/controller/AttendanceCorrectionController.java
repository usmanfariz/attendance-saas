package com.attendance.saas.controller;

import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.correction.AttendanceCorrectionCreateRequest;
import com.attendance.saas.dto.correction.AttendanceCorrectionResponse;
import com.attendance.saas.dto.leave.LeaveReviewRequest;
import com.attendance.saas.entity.enums.RequestStatus;
import com.attendance.saas.service.AttendanceCorrectionService;
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
@RequestMapping("/api/v1/attendance-corrections")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Attendance Correction")
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService correctionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Ajukan koreksi absensi karena lupa check-in atau check-out")
    public ResponseEntity<ApiResponse<AttendanceCorrectionResponse>> create(
            @Valid @RequestBody AttendanceCorrectionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        correctionService.create(request), "Pengajuan koreksi berhasil dibuat"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Daftar pengajuan koreksi absensi")
    public ApiResponse<PageResponse<AttendanceCorrectionResponse>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "attendanceDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(
                correctionService.search(employeeId, status, startDate, endDate, pageable));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Pengajuan koreksi milik sendiri")
    public ApiResponse<PageResponse<AttendanceCorrectionResponse>> myRequests(
            @RequestParam(required = false) RequestStatus status,
            @PageableDefault(size = 20, sort = "attendanceDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(correctionService.myRequests(status, pageable));
    }

    @GetMapping("/{correctionId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail pengajuan koreksi")
    public ApiResponse<AttendanceCorrectionResponse> getById(@PathVariable Long correctionId) {
        return ApiResponse.success(correctionService.getById(correctionId));
    }

    @PutMapping("/{correctionId}/approve")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Setujui koreksi dan terapkan ke data absensi",
            description = "Nilai absensi sebelum koreksi disimpan sebagai jejak audit")
    public ApiResponse<AttendanceCorrectionResponse> approve(
            @PathVariable Long correctionId, @Valid @RequestBody LeaveReviewRequest request) {
        return ApiResponse.success(
                correctionService.approve(correctionId, request), "Koreksi absensi disetujui");
    }

    @PutMapping("/{correctionId}/reject")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Tolak koreksi absensi (alasan wajib diisi)")
    public ApiResponse<AttendanceCorrectionResponse> reject(
            @PathVariable Long correctionId, @Valid @RequestBody LeaveReviewRequest request) {
        return ApiResponse.success(
                correctionService.reject(correctionId, request), "Koreksi absensi ditolak");
    }
}
