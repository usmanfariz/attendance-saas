package com.attendance.saas.service;

import com.attendance.saas.entity.AuditLog;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.repository.AuditLogRepository;
import com.attendance.saas.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records important activities.
 *
 * <p>Each entry is written in its own transaction and any failure is swallowed:
 * an audit problem must never roll back or reject the business operation that
 * triggered it. The trade-off is that an entry can outlive an action whose
 * transaction later rolled back, which is the safer direction for a log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int MAX_DESCRIPTION = 500;

    /** Headers a reverse proxy uses to pass on the original client address. */
    private static final String[] FORWARDED_HEADERS = {"X-Forwarded-For", "X-Real-IP"};

    private final AuditLogRepository auditLogRepository;

    /** Records an action performed by the currently authenticated user. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, String entity, Long entityId, String description) {
        var principal = SecurityUtils.getCurrentPrincipal().orElse(null);
        write(
                principal == null ? null : principal.companyId(),
                principal == null ? null : principal.id(),
                action, entity, entityId, description);
    }

    /**
     * Records an action whose actor is known explicitly — used at login, before
     * the security context exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFor(Long companyId,
                          Long userId,
                          AuditAction action,
                          String entity,
                          Long entityId,
                          String description) {
        write(companyId, userId, action, entity, entityId, description);
    }

    /**
     * Not transactional itself: the two public entry points carry
     * {@code REQUIRES_NEW}, and a self-invocation here would bypass the proxy.
     */
    private void write(Long companyId,
                       Long userId,
                       AuditAction action,
                       String entity,
                       Long entityId,
                       String description) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .companyId(companyId)
                    .userId(userId)
                    .action(action)
                    .entity(entity)
                    .entityId(entityId)
                    .description(truncate(description))
                    .ipAddress(currentIpAddress())
                    .build());
        } catch (RuntimeException ex) {
            // Never let bookkeeping break the operation being audited.
            log.warn("Failed to write audit log for action {} on {} {}", action, entity, entityId, ex);
        }
    }

    /**
     * Client address of the request in flight, honouring the proxy headers set
     * by the bundled Nginx configuration. Null outside a web request.
     */
    private String currentIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        for (String header : FORWARDED_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                // X-Forwarded-For may be a chain; the first entry is the client.
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String truncate(String description) {
        if (description == null || description.length() <= MAX_DESCRIPTION) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION);
    }
}
