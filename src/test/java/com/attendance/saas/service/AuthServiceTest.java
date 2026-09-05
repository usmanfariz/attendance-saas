package com.attendance.saas.service;

import com.attendance.saas.dto.auth.AuthResponse;
import com.attendance.saas.dto.auth.AuthenticatedUserResponse;
import com.attendance.saas.dto.auth.ChangePasswordRequest;
import com.attendance.saas.dto.auth.LoginRequest;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import com.attendance.saas.exception.ApiException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.mapper.UserMapper;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.JwtTokenProvider;
import com.attendance.saas.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService authService;

    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .name("PT Alpha")
                .code("alpha")
                .email("info@alpha.test")
                .timezone("Asia/Jakarta")
                .status(CompanyStatus.ACTIVE)
                .build();
        company.setId(10L);

        user = User.builder()
                .company(company)
                .name("Budi")
                .email("budi@alpha.test")
                .password("$2a$12$hashed")
                .role(UserRole.COMPANY_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        user.setId(1L);
    }

    @Test
    @DisplayName("login berhasil mengembalikan access token, refresh token, dan identitas tenant")
    void loginReturnsTokensAndTenantIdentity() {
        LoginRequest request = new LoginRequest("budi@alpha.test", "Password123!");
        when(userRepository.findByEmailIgnoreCase(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
        when(tokenProvider.accessTokenTtlSeconds()).thenReturn(3600L);
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");
        when(userMapper.toAuthenticatedUser(user)).thenReturn(new AuthenticatedUserResponse(
                1L, "Budi", "budi@alpha.test", UserRole.COMPANY_ADMIN, 10L, "PT Alpha", "alpha", "Asia/Jakarta"));

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(response.user().companyId()).isEqualTo(10L);
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("login dengan email tidak dikenal ditolak sebagai INVALID_CREDENTIALS")
    void loginWithUnknownEmailIsRejected() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@alpha.test", "Password123!")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("password salah tidak membocorkan bahwa email terdaftar")
    void loginWithWrongPasswordIsRejected() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("budi@alpha.test", "salah123A")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenService, never()).issue(any());
        verify(auditService).recordFor(
                eq(10L), eq(1L), eq(AuditAction.LOGIN_FAILED), eq("User"), eq(1L), anyString());
    }

    @Test
    @DisplayName("user nonaktif tidak boleh login")
    void inactiveUserCannotLogin() {
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("budi@alpha.test", "Password123!")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_INACTIVE);
    }

    @Test
    @DisplayName("user dari perusahaan yang di-suspend tidak boleh login")
    void userOfSuspendedCompanyCannotLogin() {
        company.setStatus(CompanyStatus.SUSPENDED);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("budi@alpha.test", "Password123!")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_INACTIVE);
    }

    @Test
    @DisplayName("refresh token yang valid menghasilkan pasangan token baru")
    void refreshIssuesNewTokenPair() {
        when(refreshTokenService.consume("old-refresh")).thenReturn(user);
        when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new-access");
        when(refreshTokenService.issue(user)).thenReturn("new-refresh");
        when(userMapper.toAuthenticatedUser(user)).thenReturn(new AuthenticatedUserResponse(
                1L, "Budi", "budi@alpha.test", UserRole.COMPANY_ADMIN, 10L, "PT Alpha", "alpha", "Asia/Jakarta"));

        AuthResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    @DisplayName("ganti password mencabut seluruh refresh token milik user")
    void changePasswordRevokesExistingSessions() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1", user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("NewPassword1")).thenReturn("$2a$12$newhash");

        authService.changePassword(1L, new ChangePasswordRequest("Password123!", "NewPassword1"));

        assertThat(user.getPassword()).isEqualTo("$2a$12$newhash");
        verify(refreshTokenService).revokeAllForUser(1L);
    }

    @Test
    @DisplayName("ganti password menolak password lama yang salah")
    void changePasswordRejectsWrongCurrentPassword() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(
                1L, new ChangePasswordRequest("salah123A", "NewPassword1")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }
}
