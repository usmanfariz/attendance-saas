package com.attendance.saas.service;

import com.attendance.saas.config.JwtProperties;
import com.attendance.saas.entity.RefreshToken;
import com.attendance.saas.entity.User;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.UnauthorizedException;
import com.attendance.saas.repository.RefreshTokenRepository;
import com.attendance.saas.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues opaque refresh tokens and rotates them on every use, so a replayed
 * token is detected and the whole family revoked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .companyId(user.getCompanyId())
                .tokenHash(TokenHasher.sha256Hex(rawToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()))
                .build();
        refreshTokenRepository.save(entity);

        return rawToken;
    }

    /**
     * Consumes a refresh token and returns the owning user.
     *
     * @throws UnauthorizedException when the token is unknown, revoked or expired.
     */
    @Transactional
    public User consume(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.INVALID_TOKEN, "Refresh token tidak valid"));

        Instant now = Instant.now();
        if (stored.isRevoked()) {
            // Re-use of an already rotated token: assume theft and cut the family.
            log.warn("Refresh token reuse detected for user id {}", stored.getUser().getId());
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), now);
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Refresh token sudah tidak berlaku");
        }
        if (stored.isExpired(now)) {
            throw new UnauthorizedException(ErrorCode.TOKEN_EXPIRED, "Refresh token sudah kedaluwarsa");
        }

        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);
        return stored.getUser();
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public void revokeSingle(String rawToken) {
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
    }
}
