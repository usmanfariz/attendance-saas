package com.attendance.saas.service;

import com.attendance.saas.dto.audit.AuditLogResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.AuditLog;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.repository.AuditLogRepository;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read side of the audit trail. Writing lives in {@link AuditService}; keeping
 * the two apart means a query can never accidentally create an entry.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(Long userId,
                                                 AuditAction action,
                                                 String entity,
                                                 LocalDate startDate,
                                                 LocalDate endDate,
                                                 Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();

        Page<AuditLog> page = auditLogRepository.search(
                companyId,
                userId,
                action,
                entity,
                startDate == null ? null : startOfDay(startDate),
                endDate == null ? null : endOfDay(endDate),
                pageable);

        // One lookup for the whole page rather than one per row.
        Map<Long, String> userNames = userRepository
                .findAllById(page.getContent().stream()
                        .map(AuditLog::getUserId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getName()));

        Function<AuditLog, AuditLogResponse> mapper = log -> new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getUserId() == null ? null : userNames.get(log.getUserId()),
                log.getAction(),
                log.getEntity(),
                log.getEntityId(),
                log.getDescription(),
                log.getIpAddress(),
                log.getCreatedAt());

        return PageResponse.of(page, mapper);
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneId.of("UTC")).toInstant();
    }

    private Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().minusMillis(1);
    }
}
