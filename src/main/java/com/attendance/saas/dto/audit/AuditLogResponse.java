package com.attendance.saas.dto.audit;

import com.attendance.saas.entity.enums.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "AuditLogResponse")
public record AuditLogResponse(
        Long id,
        Long userId,
        String userName,
        AuditAction action,
        String entity,
        Long entityId,
        String description,
        String ipAddress,
        Instant createdAt
) {
}
