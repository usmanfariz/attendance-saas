package com.attendance.saas.service;

import com.attendance.saas.dto.billing.AssignPlanRequest;
import com.attendance.saas.dto.billing.SubscriptionResponse;
import com.attendance.saas.dto.billing.SubscriptionUsageResponse;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.Subscription;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.BillingMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.SubscriptionRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Assigning and changing the plan a company is on.
 *
 * <p>Plan changes take effect immediately and start a fresh billing period, so
 * an upgrade is usable at once. Existing data is never removed when moving to a
 * smaller plan: the new limits only stop further growth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    /** Plan every self-service sign-up starts on. */
    public static final String DEFAULT_PLAN_CODE = "FREE";

    private final SubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;
    private final PlanService planService;
    private final PlanLimitService planLimitService;
    private final BillingService billingService;
    private final BillingMapper billingMapper;
    private final AuditService auditService;
    private final Clock clock;

    /** Provisions the free subscription that comes with a new tenant. */
    @Transactional
    public Subscription createDefaultSubscription(Company company) {
        Plan plan = planService.requireByCode(DEFAULT_PLAN_CODE);
        LocalDate today = today();

        Subscription subscription = Subscription.builder()
                .company(company)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(today)
                .currentPeriodStart(today)
                .currentPeriodEnd(plan.getBillingPeriod().endOfPeriod(today))
                .autoRenew(true)
                .build();
        subscriptionRepository.save(subscription);

        log.info("Company {} subscribed to default plan {}", company.getId(), plan.getCode());
        return subscription;
    }

    /** Super admin: the platform-wide subscription list. */
    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> search(SubscriptionStatus status,
                                                     Long planId,
                                                     Pageable pageable) {
        return PageResponse.of(
                subscriptionRepository.search(status, planId, pageable), billingMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getForCompany(Long companyId) {
        SecurityUtils.assertCanAccessCompany(companyId);
        return billingMapper.toResponse(load(companyId));
    }

    /** Company admin: own plan plus how much of it is used. */
    @Transactional(readOnly = true)
    public SubscriptionUsageResponse getOwnSubscriptionWithUsage() {
        Long companyId = SecurityUtils.requireCompanyId();
        Subscription subscription = load(companyId);
        Plan plan = subscription.getPlan();

        return new SubscriptionUsageResponse(
                billingMapper.toResponse(subscription),
                planLimitService.employeeUsage(companyId, plan),
                planLimitService.locationUsage(companyId, plan),
                planLimitService.userUsage(companyId, plan),
                plan.isGeofenceIncluded());
    }

    /**
     * Super admin: moves a company onto a plan, optionally with a trial.
     * The change starts a new billing period and, for a paid plan taking effect
     * immediately, issues its first invoice.
     */
    @Transactional
    public SubscriptionResponse assignPlan(Long companyId, AssignPlanRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.COMPANY_NOT_FOUND, "Perusahaan tidak ditemukan"));
        Plan plan = planService.require(request.planId());

        if (!plan.isActive()) {
            throw new BusinessException(
                    ErrorCode.PLAN_INACTIVE, "Plan " + plan.getName() + " sudah tidak aktif");
        }

        LocalDate today = today();
        int trialDays = request.trialDays() == null ? 0 : request.trialDays();

        Subscription subscription = subscriptionRepository.findByCompanyId(companyId)
                .orElseGet(() -> Subscription.builder()
                        .company(company)
                        .startDate(today)
                        .build());

        String previousPlan = subscription.getPlan() == null ? "-" : subscription.getPlan().getCode();

        subscription.setCompany(company);
        subscription.setPlan(plan);
        subscription.setCurrentPeriodStart(today);
        subscription.setCurrentPeriodEnd(plan.getBillingPeriod().endOfPeriod(today));
        subscription.setAutoRenew(request.autoRenew() == null || request.autoRenew());
        subscription.setCancelledAt(null);

        if (trialDays > 0) {
            subscription.setStatus(SubscriptionStatus.TRIAL);
            subscription.setTrialEndsAt(today.plusDays(trialDays));
        } else {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setTrialEndsAt(null);
        }
        if (subscription.getStartDate() == null) {
            subscription.setStartDate(today);
        }
        subscriptionRepository.save(subscription);

        billingService.issueInvoiceForCurrentPeriod(subscription);

        auditService.record(AuditAction.UPDATE, "Subscription", subscription.getId(),
                "Plan perusahaan %d: %s -> %s".formatted(companyId, previousPlan, plan.getCode()));
        log.info("Company {} moved from plan {} to {}", companyId, previousPlan, plan.getCode());

        return billingMapper.toResponse(subscription);
    }

    /**
     * Cancels auto-renewal. The tenant keeps its entitlements until the paid
     * period ends, which is what a customer expects from a cancellation.
     */
    @Transactional
    public SubscriptionResponse cancel(Long companyId) {
        Subscription subscription = load(companyId);

        subscription.setAutoRenew(false);
        subscription.setCancelledAt(clock.instant());
        subscription.setStatus(SubscriptionStatus.CANCELLED);

        auditService.record(AuditAction.UPDATE, "Subscription", subscription.getId(),
                "Membatalkan langganan perusahaan " + companyId);
        log.info("Subscription of company {} cancelled, runs until {}",
                companyId, subscription.getCurrentPeriodEnd());

        return billingMapper.toResponse(subscription);
    }

    /** Puts a cancelled or expired subscription back into service. */
    @Transactional
    public SubscriptionResponse reactivate(Long companyId) {
        Subscription subscription = load(companyId);
        LocalDate today = today();

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setAutoRenew(true);
        subscription.setCancelledAt(null);

        if (subscription.isPeriodOver(today)) {
            subscription.setCurrentPeriodStart(today);
            subscription.setCurrentPeriodEnd(
                    subscription.getPlan().getBillingPeriod().endOfPeriod(today));
            billingService.issueInvoiceForCurrentPeriod(subscription);
        }

        auditService.record(AuditAction.UPDATE, "Subscription", subscription.getId(),
                "Mengaktifkan kembali langganan perusahaan " + companyId);
        return billingMapper.toResponse(subscription);
    }

    private Subscription load(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "Perusahaan ini belum memiliki langganan"));
    }

    private LocalDate today() {
        return clock.instant().atZone(ZoneId.of("UTC")).toLocalDate();
    }
}
