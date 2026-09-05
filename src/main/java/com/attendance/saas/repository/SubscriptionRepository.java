package com.attendance.saas.repository;

import com.attendance.saas.entity.Subscription;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan
            WHERE s.company.id = :companyId
            """)
    Optional<Subscription> findByCompanyId(@Param("companyId") Long companyId);

    long countByPlan_Id(Long planId);

    long countByStatus(SubscriptionStatus status);

    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan
            JOIN FETCH s.company
            WHERE (:status IS NULL OR s.status = :status)
              AND (:planId IS NULL OR s.plan.id = :planId)
            """)
    Page<Subscription> search(@Param("status") SubscriptionStatus status,
                              @Param("planId") Long planId,
                              Pageable pageable);

    /**
     * Subscriptions whose paid period has run out. CANCELLED ones are included
     * so the sweep can retire them: cancelling stops the renewal, the period
     * end is what actually expires the subscription.
     */
    @Query("""
            SELECT s FROM Subscription s
            JOIN FETCH s.plan
            JOIN FETCH s.company
            WHERE s.currentPeriodEnd < :today
              AND s.status IN (com.attendance.saas.entity.enums.SubscriptionStatus.ACTIVE,
                               com.attendance.saas.entity.enums.SubscriptionStatus.TRIAL,
                               com.attendance.saas.entity.enums.SubscriptionStatus.PAST_DUE,
                               com.attendance.saas.entity.enums.SubscriptionStatus.CANCELLED)
            """)
    List<Subscription> findDueForRenewal(@Param("today") LocalDate today);

    @Query("SELECT s.plan.code, COUNT(s) FROM Subscription s GROUP BY s.plan.code")
    List<Object[]> countByPlanCode();
}
