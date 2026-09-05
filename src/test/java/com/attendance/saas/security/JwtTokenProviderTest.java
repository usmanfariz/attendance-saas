package com.attendance.saas.security;

import com.attendance.saas.config.JwtProperties;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.exception.ApiException;
import com.attendance.saas.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET =
            "unit-test-secret-key-that-is-long-enough-for-hs512-signing-algorithm!!";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties(SECRET, "attendance-saas", 60, 14));

    @Test
    @DisplayName("access token membawa user id, role, dan company id")
    void tokenCarriesTenantClaims() {
        UserPrincipal principal = new UserPrincipal(
                7L, "hr@alpha.test", "HR Alpha", null, UserRole.HR, 10L, true);

        Claims claims = provider.parseClaims(provider.generateAccessToken(principal));

        assertThat(claims.getSubject()).isEqualTo("hr@alpha.test");
        assertThat(claims.get(JwtTokenProvider.CLAIM_USER_ID, Number.class).longValue()).isEqualTo(7L);
        assertThat(claims.get(JwtTokenProvider.CLAIM_COMPANY_ID, Number.class).longValue()).isEqualTo(10L);
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("HR");
    }

    @Test
    @DisplayName("principal super admin di-roundtrip dengan companyId null")
    void superAdminRoundTripsWithNullCompany() {
        UserPrincipal principal = new UserPrincipal(
                1L, "root@attendance.local", "Root", null, UserRole.SUPER_ADMIN, null, true);

        UserPrincipal parsed = provider.toPrincipal(
                provider.parseClaims(provider.generateAccessToken(principal)));

        assertThat(parsed.companyId()).isNull();
        assertThat(parsed.isSuperAdmin()).isTrue();
        assertThat(parsed.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("token yang dimanipulasi ditolak")
    void tamperedTokenIsRejected() {
        UserPrincipal principal = new UserPrincipal(
                7L, "hr@alpha.test", "HR Alpha", null, UserRole.HR, 10L, true);
        String token = provider.generateAccessToken(principal);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> provider.parseClaims(tampered))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("token yang ditandatangani secret lain ditolak")
    void tokenFromForeignSecretIsRejected() {
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(
                "another-secret-key-that-is-also-long-enough-for-hs512-signing!!!!!", "attendance-saas", 60, 14));
        String foreignToken = other.generateAccessToken(new UserPrincipal(
                7L, "hr@beta.test", "HR Beta", null, UserRole.HR, 20L, true));

        assertThatThrownBy(() -> provider.parseClaims(foreignToken))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("token dari issuer lain ditolak")
    void tokenFromForeignIssuerIsRejected() {
        JwtTokenProvider other = new JwtTokenProvider(
                new JwtProperties(SECRET, "other-issuer", 60, 14));
        String foreignToken = other.generateAccessToken(new UserPrincipal(
                7L, "hr@beta.test", "HR Beta", null, UserRole.HR, 20L, true));

        assertThatThrownBy(() -> provider.parseClaims(foreignToken))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
