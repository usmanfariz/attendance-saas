package com.attendance.saas.repository;

import com.attendance.saas.entity.AuditLog;
import com.attendance.saas.entity.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT al FROM AuditLog al
            WHERE al.companyId = :companyId
              AND (:userId IS NULL OR al.userId = :userId)
              AND (:action IS NULL OR al.action = :action)
              AND (:entity IS NULL OR al.entity = :entity)
              AND (:from   IS NULL OR al.createdAt >= :from)
              AND (:to     IS NULL OR al.createdAt <= :to)
            """)
    Page<AuditLog> search(@Param("companyId") Long companyId,
                          @Param("userId") Long userId,
                          @Param("action") AuditAction action,
                          @Param("entity") String entity,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);
}
