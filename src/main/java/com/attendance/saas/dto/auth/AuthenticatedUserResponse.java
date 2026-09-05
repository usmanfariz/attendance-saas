package com.attendance.saas.dto.auth;

import com.attendance.saas.entity.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthenticatedUser")
public record AuthenticatedUserResponse(
        Long id,
        String name,
        String email,
        UserRole role,
        Long companyId,
        String companyName,
        String companyCode,
        String timezone
) {
}
