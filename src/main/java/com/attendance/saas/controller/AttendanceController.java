package com.attendance.saas.controller;

import com.attendance.saas.dto.attendance.AttendanceResponse;
import com.attendance.saas.dto.attendance.CheckInRequest;
import com.attendance.saas.dto.attendance.CheckOutRequest;
import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.service.AttendanceQueryService;
import com.attendance.saas.service.AttendanceService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceQueryService attendanceQueryService;

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Check-in",
            description = """
                    Shift dan tanggal absensi ditentukan otomatis dari jadwal karyawan
                    dan waktu perusahaan. Untuk shift malam, check-in pukul 00:30 tetap
                    tercatat pada tanggal shift dimulai.
                    """)
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(attendanceService.checkIn(request), "Check-in berhasil"));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Check-out",
            description = "Untuk shift malam, check-out dapat terjadi pada hari berikutnya")
    public ApiResponse<AttendanceResponse> checkOut(@Valid @RequestBody CheckOutRequest request) {
        return ApiResponse.success(attendanceService.checkOut(request), "Check-out berhasil");
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Absensi yang sedang berjalan atau hari ini")
    public ApiResponse<AttendanceResponse> current() {
        return ApiResponse.success(attendanceService.myCurrentAttendance().orElse(null));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('HR','SUPERVISOR','EMPLOYEE')")
    @Operation(summary = "Riwayat absensi milik karyawan yang sedang login")
    public ApiResponse<PageResponse<AttendanceResponse>> myHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 31, sort = "attendanceDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(attendanceQueryService.myHistory(startDate, endDate, pageable));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Daftar absensi seluruh karyawan dengan filter")
    public ApiResponse<PageResponse<AttendanceResponse>> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) AttendanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20, sort = "attendanceDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.success(attendanceQueryService.search(
                employeeId, departmentId, shiftId, status, startDate, endDate, pageable));
    }

    @GetMapping("/{attendanceId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','HR','SUPERVISOR')")
    @Operation(summary = "Detail satu data absensi")
    public ApiResponse<AttendanceResponse> getById(@PathVariable Long attendanceId) {
        return ApiResponse.success(attendanceQueryService.getById(attendanceId));
    }
}
