package com.attendance.saas.controller;

import com.attendance.saas.dto.auth.AuthResponse;
import com.attendance.saas.dto.auth.AuthenticatedUserResponse;
import com.attendance.saas.dto.auth.ChangePasswordRequest;
import com.attendance.saas.dto.auth.LoginRequest;
import com.attendance.saas.dto.auth.RefreshTokenRequest;
import com.attendance.saas.dto.auth.RegisterCompanyRequest;
import com.attendance.saas.dto.common.ApiResponse;
import com.attendance.saas.dto.company.RegisterCompanyResponse;
import com.attendance.saas.security.SecurityUtils;
import com.attendance.saas.security.UserPrincipal;
import com.attendance.saas.service.AuthService;
import com.attendance.saas.service.CompanyRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final CompanyRegistrationService companyRegistrationService;

    @PostMapping("/login")
    @Operation(summary = "Login dengan email dan password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), "Login berhasil");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Tukar refresh token menjadi access token baru")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(
                authService.refresh(request.refreshToken()), "Token berhasil diperbarui");
    }

    @PostMapping("/register-company")
    @Operation(summary = "Registrasi perusahaan baru beserta akun admin pertamanya")
    public ResponseEntity<ApiResponse<RegisterCompanyResponse>> registerCompany(
            @Valid @RequestBody RegisterCompanyRequest request) {
        RegisterCompanyResponse response = companyRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Perusahaan berhasil didaftarkan"));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout dan cabut seluruh refresh token milik user")
    public ApiResponse<Void> logout() {
        authService.logout(SecurityUtils.requireUserId());
        return ApiResponse.message("Logout berhasil");
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Profil user yang sedang login")
    public ApiResponse<AuthenticatedUserResponse> me() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        return ApiResponse.success(authService.currentUser(principal));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Ubah password user yang sedang login")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.requireUserId(), request);
        return ApiResponse.message("Password berhasil diubah, silakan login kembali");
    }
}
