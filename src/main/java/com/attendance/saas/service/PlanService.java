package com.attendance.saas.service;

import com.attendance.saas.dto.billing.PlanRequest;
import com.attendance.saas.dto.billing.PlanResponse;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.BillingMapper;
import com.attendance.saas.repository.PlanRepository;
import com.attendance.saas.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * The platform plan catalogue, curated by the super admin.
 *
 * <p>Not tenant-scoped: plans are shared across every company, so nothing here
 * reads {@code SecurityUtils.requireCompanyId()}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingMapper billingMapper;
    private final AuditService auditService;

    /** Every plan, including retired ones — super admin view. */
    @Transactional(readOnly = true)
    public List<PlanResponse> listAll() {
        return planRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(plan -> billingMapper.toResponse(
                        plan, subscriptionRepository.countByPlan_Id(plan.getId())))
                .toList();
    }

    /** Plans a tenant could actually move to. */
    @Transactional(readOnly = true)
    public List<PlanResponse> listAvailable() {
        return planRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(plan -> billingMapper.toResponse(plan, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getById(Long planId) {
        Plan plan = load(planId);
        return billingMapper.toResponse(plan, subscriptionRepository.countByPlan_Id(planId));
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {
        String code = request.code().trim().toUpperCase();
        if (planRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException(
                    ErrorCode.PLAN_CODE_ALREADY_EXISTS, "Kode plan sudah digunakan");
        }

        Plan plan = Plan.builder()
                .code(code)
                .name(request.name().trim())
                .description(trimToNull(request.description()))
                .priceAmount(request.priceAmount() == null ? BigDecimal.ZERO : request.priceAmount())
                .currency(currencyOrDefault(request.currency()))
                .billingPeriod(request.billingPeriod())
                .maxEmployees(request.maxEmployees())
                .maxLocations(request.maxLocations())
                .maxUsers(request.maxUsers())
                .geofenceIncluded(Boolean.TRUE.equals(request.geofenceIncluded()))
                .active(request.active() == null || request.active())
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .build();
        planRepository.save(plan);

        auditService.record(AuditAction.CREATE, "Plan", plan.getId(), "Menambah plan " + code);
        log.info("Plan {} ({}) created", plan.getId(), code);
        return billingMapper.toResponse(plan, 0L);
    }

    @Transactional
    public PlanResponse update(Long planId, PlanRequest request) {
        Plan plan = load(planId);
        String code = request.code().trim().toUpperCase();

        if (planRepository.existsByCodeIgnoreCaseAndIdNot(code, planId)) {
            throw new ConflictException(
                    ErrorCode.PLAN_CODE_ALREADY_EXISTS, "Kode plan sudah digunakan");
        }

        // Repricing affects future invoices only; issued ones keep their amount.
        plan.setCode(code);
        plan.setName(request.name().trim());
        plan.setDescription(trimToNull(request.description()));
        plan.setPriceAmount(request.priceAmount() == null ? BigDecimal.ZERO : request.priceAmount());
        plan.setCurrency(currencyOrDefault(request.currency()));
        plan.setBillingPeriod(request.billingPeriod());
        plan.setMaxEmployees(request.maxEmployees());
        plan.setMaxLocations(request.maxLocations());
        plan.setMaxUsers(request.maxUsers());
        plan.setGeofenceIncluded(Boolean.TRUE.equals(request.geofenceIncluded()));
        plan.setActive(request.active() == null || request.active());
        plan.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());

        auditService.record(AuditAction.UPDATE, "Plan", planId, "Mengubah plan " + code);
        log.info("Plan {} updated", planId);
        return billingMapper.toResponse(plan, subscriptionRepository.countByPlan_Id(planId));
    }

    /**
     * A plan with subscribers is never deleted — that would orphan a tenant.
     * Retire it by setting {@code active = false} instead.
     */
    @Transactional
    public void delete(Long planId) {
        Plan plan = load(planId);

        long subscribers = subscriptionRepository.countByPlan_Id(planId);
        if (subscribers > 0) {
            throw new ConflictException(
                    ErrorCode.PLAN_IN_USE,
                    "Plan masih dipakai %d perusahaan; nonaktifkan saja agar tidak dapat dipilih lagi"
                            .formatted(subscribers));
        }

        planRepository.delete(plan);
        auditService.record(AuditAction.DELETE, "Plan", planId, "Menghapus plan " + plan.getCode());
        log.info("Plan {} deleted", planId);
    }

    @Transactional(readOnly = true)
    public Plan require(Long planId) {
        return load(planId);
    }

    @Transactional(readOnly = true)
    public Plan requireByCode(String code) {
        return planRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PLAN_NOT_FOUND, "Plan " + code + " tidak ditemukan"));
    }

    private Plan load(Long planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PLAN_NOT_FOUND, "Plan tidak ditemukan"));
    }

    private String currencyOrDefault(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : "IDR";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
