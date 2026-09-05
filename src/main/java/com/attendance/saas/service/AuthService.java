package com.attendance.saas.service;

import com.attendance.saas.dto.auth.AuthResponse;
import com.attendance.saas.dto.auth.AuthenticatedUserResponse;
import com.attendance.saas.dto.auth.ChangePasswordRequest;
import com.attendance.saas.dto.auth.LoginRequest;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.BadRequestException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ForbiddenException;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.exception.UnauthorizedException;
import com.attendance.saas.mapper.UserMapper;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.JwtTokenProvider;
import com.attendance.saas.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Authentication use cases: login, token refresh, logout and password change.
 *
 * <p>Never logs credentials or tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.INVALID_CREDENTIALS, "Email atau password salah"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("Failed login attempt for user id {}", user.getId());
            auditService.recordFor(user.getCompanyId(), user.getId(),
                    AuditAction.LOGIN_FAILED, "User", user.getId(), "Password salah");
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Email atau password salah");
        }

        assertAccountUsable(user);

        user.setLastLoginAt(Instant.now());
        log.info("User {} logged in (company {})", user.getId(), user.getCompanyId());
        auditService.recordFor(user.getCompanyId(), user.getId(),
                AuditAction.LOGIN, "User", user.getId(), "Login berhasil");

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken);
        assertAccountUsable(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(AuditAction.LOGOUT, "User", userId, "Logout");
        log.info("User {} logged out", userId);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser(UserPrincipal principal) {
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User tidak ditemukan"));
        return userMapper.toAuthenticatedUser(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User tidak ditemukan"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BadRequestException(ErrorCode.INVALID_CREDENTIALS, "Password lama tidak sesuai");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException(
                    ErrorCode.WEAK_PASSWORD, "Password baru tidak boleh sama dengan password lama");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // Force every existing session to re-authenticate with the new password.
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(AuditAction.UPDATE, "User", userId, "Ganti password");
        log.info("Password changed for user {}", userId);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserPrincipal principal = UserPrincipal.from(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                tokenProvider.accessTokenTtlSeconds(),
                userMapper.toAuthenticatedUser(user));
    }

    private void assertAccountUsable(User user) {
        if (!user.isActive()) {
            throw new ForbiddenException(ErrorCode.USER_INACTIVE, "Akun Anda tidak aktif");
        }
        Company company = user.getCompany();
        if (company != null && !company.isActive()) {
            throw new ForbiddenException(
                    ErrorCode.COMPANY_INACTIVE, "Perusahaan Anda sedang tidak aktif");
        }
    }
}
