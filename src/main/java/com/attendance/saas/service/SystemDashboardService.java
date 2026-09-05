package com.attendance.saas.service;

import com.attendance.saas.dto.billing.SystemDashboardResponse;
import com.attendance.saas.entity.Subscription;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.InvoiceStatus;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.InvoiceRepository;
import com.attendance.saas.repository.SubscriptionRepository;
import com.attendance.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Platform-wide view for the super admin (specification section 5).
 *
 * <p>The only service that deliberately reads across tenants; every method here
 * sits behind {@code hasRole('SUPER_ADMIN')} at the controller.
 */
@Service
@RequiredArgsConstructor
public class SystemDashboardService {

    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SystemDashboardResponse systemDashboard() {
        long totalCompanies = companyRepository.count();
        long activeCompanies = companyRepository
                .search(null, CompanyStatus.ACTIVE, PageRequest.of(0, 1)).getTotalElements();
        long suspendedCompanies = companyRepository
                .search(null, CompanyStatus.SUSPENDED, PageRequest.of(0, 1)).getTotalElements();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SubscriptionStatus status : SubscriptionStatus.values()) {
            byStatus.put(status.name(), subscriptionRepository.countByStatus(status));
        }

        List<SystemDashboardResponse.PlanDistribution> byPlan =
                subscriptionRepository.countByPlanCode().stream()
                        .map(row -> new SystemDashboardResponse.PlanDistribution(
                                (String) row[0], ((Number) row[1]).longValue()))
                        .toList();

        LocalDate today = clock.instant().atZone(ZoneId.of("UTC")).toLocalDate();
        LocalDate monthStart = today.withDayOfMonth(1);

        return new SystemDashboardResponse(
                totalCompanies,
                activeCompanies,
                suspendedCompanies,
                employeeRepository.count(),
                userRepository.count(),
                byStatus,
                byPlan,
                monthlyRecurringRevenue(),
                "IDR",
                invoiceRepository.countByStatus(InvoiceStatus.ISSUED),
                invoiceRepository.countByStatus(InvoiceStatus.OVERDUE),
                invoiceRepository.sumPaidBetween(monthStart, today.withDayOfMonth(today.lengthOfMonth())));
    }

    /**
     * Recurring revenue normalised to a month: yearly plans contribute a
     * twelfth, so plans on different billing periods stay comparable. Trials
     * and cancelled subscriptions are excluded — they are not yet revenue.
     */
    private BigDecimal monthlyRecurringRevenue() {
        return subscriptionRepository.findAll().stream()
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE)
                .map(this::normalisedMonthlyPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal normalisedMonthlyPrice(Subscription subscription) {
        BigDecimal price = subscription.getPlan().getPriceAmount();
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return switch (subscription.getPlan().getBillingPeriod()) {
            case MONTHLY -> price;
            case YEARLY -> price.divide(BigDecimal.valueOf(12), 2, java.math.RoundingMode.HALF_UP);
        };
    }
}
