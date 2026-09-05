package com.attendance.saas.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        /** HMAC key material; must be long enough for HS512 (>= 64 bytes). */
        @NotBlank @Size(min = 64, message = "app.jwt.secret minimal 64 karakter")
        String secret,

        @NotBlank String issuer,

        @Min(1) long accessTokenExpirationMinutes,

        @Min(1) long refreshTokenExpirationDays
) {

    public Duration accessTokenTtl() {
        return Duration.ofMinutes(accessTokenExpirationMinutes);
    }

    public Duration refreshTokenTtl() {
        return Duration.ofDays(refreshTokenExpirationDays);
    }
}
