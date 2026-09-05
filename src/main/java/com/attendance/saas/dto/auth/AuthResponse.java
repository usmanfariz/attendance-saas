package com.attendance.saas.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse")
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        AuthenticatedUserResponse user
) {

    public static AuthResponse of(String accessToken,
                                  String refreshToken,
                                  long expiresInSeconds,
                                  AuthenticatedUserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
