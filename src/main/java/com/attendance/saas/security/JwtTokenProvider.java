package com.attendance.saas.security;

import com.attendance.saas.config.JwtProperties;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates access tokens. The tenant claim ({@code companyId}) is
 * embedded here and is the only source of truth for tenant scoping.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_COMPANY_ID = "companyId";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_NAME = "name";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(principal.email())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        CLAIM_USER_ID, principal.id(),
                        CLAIM_COMPANY_ID, principal.companyId() == null ? -1L : principal.companyId(),
                        CLAIM_ROLE, principal.role().name(),
                        CLAIM_NAME, principal.name()))
                .signWith(signingKey)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    /**
     * @throws UnauthorizedException when the token is expired, tampered with or
     *                               otherwise unusable.
     */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Token sudah kedaluwarsa");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Token tidak valid");
        }
    }

    /**
     * Rebuilds the principal straight from the signed claims, so an
     * authenticated request costs no database round-trip.
     */
    public UserPrincipal toPrincipal(Claims claims) {
        Long userId = claims.get(CLAIM_USER_ID, Number.class).longValue();
        long rawCompanyId = claims.get(CLAIM_COMPANY_ID, Number.class).longValue();
        UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));

        return new UserPrincipal(
                userId,
                claims.getSubject(),
                claims.get(CLAIM_NAME, String.class),
                null,
                role,
                rawCompanyId < 0 ? null : rawCompanyId,
                true);
    }
}
