package com.attendance.saas.repository;

import com.attendance.saas.entity.Invoice;
import com.attendance.saas.entity.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByIdAndCompany_Id(Long id, Long companyId);

    boolean existsBySubscription_IdAndPeriodStart(Long subscriptionId, LocalDate periodStart);

    Page<Invoice> findByCompany_IdOrderByIssuedAtDesc(Long companyId, Pageable pageable);

    @Query("""
            SELECT i FROM Invoice i
            JOIN FETCH i.company
            WHERE (:companyId IS NULL OR i.company.id = :companyId)
              AND (:status IS NULL OR i.status = :status)
            """)
    Page<Invoice> search(@Param("companyId") Long companyId,
                         @Param("status") InvoiceStatus status,
                         Pageable pageable);

    /** Issued invoices whose due date has passed, for the overdue sweep. */
    @Query("""
            SELECT i FROM Invoice i
            WHERE i.status = com.attendance.saas.entity.enums.InvoiceStatus.ISSUED
              AND i.dueDate < :today
            """)
    List<Invoice> findOverdue(@Param("today") LocalDate today);

    @Query("""
            SELECT COALESCE(SUM(i.amount), 0)
            FROM Invoice i
            WHERE i.status = com.attendance.saas.entity.enums.InvoiceStatus.PAID
              AND i.periodStart BETWEEN :from AND :to
            """)
    BigDecimal sumPaidBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatus(InvoiceStatus status);
}
