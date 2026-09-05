package com.attendance.saas.service;

import com.attendance.saas.dto.billing.SubscriptionUsageResponse;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.Subscription;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.LocationRepository;
import com.attendance.saas.repository.SubscriptionRepository;
import com.attendance.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces what a tenant's plan entitles it to.
 *
 * <p>Every guard here is called <em>before</em> anything is written, so a
 * rejected request leaves no trace. Limits are checked against live counts
 * rather than a cached counter: the numbers involved are small, and a counter
 * that drifts would either block a paying customer or let a plan be exceeded.
 *
 * <p>Existing data is never taken away when a tenant downgrades below its
 * current usage — the limit only blocks growth. Removing employees a company
 * already has would be the wrong thing for a billing rule to do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanLimitService {

    private final SubscriptionRepository subscriptionRepository;
    private final EmployeeRepository employeeRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Subscription requireSubscription(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.SUBSCRIPTION_NOT_FOUND,
                        "Perusahaan ini belum memiliki langganan"));
    }

    @Transactional(readOnly = true)
    public void assertCanAddEmployee(Long companyId) {
        Plan plan = activePlan(companyId);
        long used = employeeRepository.countByCompany_Id(companyId);
        assertBelowLimit(used, plan.getMaxEmployees(), ErrorCode.EMPLOYEE_LIMIT_REACHED,
                "karyawan", plan);
    }

    @Transactional(readOnly = true)
    public void assertCanAddLocation(Long companyId) {
        Plan plan = activePlan(companyId);
        long used = locationRepository.findByCompany_IdAndActiveTrue(companyId).size();
        assertBelowLimit(used, plan.getMaxLocations(), ErrorCode.LOCATION_LIMIT_REACHED,
                "lokasi kantor", plan);
    }

    @Transactional(readOnly = true)
    public void assertCanAddUser(Long companyId) {
        Plan plan = activePlan(companyId);
        long used = userRepository.countByCompany_Id(companyId);
        assertBelowLimit(used, plan.getMaxUsers(), ErrorCode.USER_LIMIT_REACHED,
                "akun pengguna", plan);
    }

    /**
     * Geofencing is a paid feature: a tenant on a plan without it cannot switch
     * it on, and if it was already on when the plan was downgraded the
     * enforcement simply stops applying.
     */
    @Transactional(readOnly = true)
    public void assertGeofenceIncluded(Long companyId) {
        Plan plan = activePlan(companyId);
        if (!plan.isGeofenceIncluded()) {
            throw new BusinessException(
                    ErrorCode.FEATURE_NOT_IN_PLAN,
                    "Validasi lokasi tidak termasuk dalam plan " + plan.getName());
        }
    }

    @Transactional(readOnly = true)
    public boolean isGeofenceIncluded(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .map(subscription -> subscription.getPlan().isGeofenceIncluded())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageResponse.Usage employeeUsage(Long companyId, Plan plan) {
        return SubscriptionUsageResponse.Usage.of(
                employeeRepository.countByCompany_Id(companyId), plan.getMaxEmployees());
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageResponse.Usage locationUsage(Long companyId, Plan plan) {
        return SubscriptionUsageResponse.Usage.of(
                locationRepository.findByCompany_IdAndActiveTrue(companyId).size(),
                plan.getMaxLocations());
    }

    @Transactional(readOnly = true)
    public SubscriptionUsageResponse.Usage userUsage(Long companyId, Plan plan) {
        return SubscriptionUsageResponse.Usage.of(
                userRepository.countByCompany_Id(companyId), plan.getMaxUsers());
    }

    /**
     * The plan backing a subscription that is allowed to grow.
     *
     * @throws BusinessException when the subscription is cancelled, expired or
     *                           past due — the tenant keeps reading its data but
     *                           cannot add to it.
     */
    private Plan activePlan(Long companyId) {
        Subscription subscription = requireSubscription(companyId);

        if (!subscription.allowsProvisioning()) {
            throw new BusinessException(
                    ErrorCode.SUBSCRIPTION_NOT_ACTIVE,
                    "Langganan berstatus %s, tidak dapat menambah data baru"
                            .formatted(subscription.getStatus()));
        }
        return subscription.getPlan();
    }

    private void assertBelowLimit(long used, Integer limit, ErrorCode errorCode,
                                  String what, Plan plan) {
        if (limit == null) {
            return;
        }
        if (used >= limit) {
            log.info("Plan limit reached: {} of {} {} on plan {}", used, limit, what, plan.getCode());
            throw new BusinessException(
                    errorCode,
                    "Batas %d %s pada plan %s sudah tercapai, silakan upgrade"
                            .formatted(limit, what, plan.getName()));
        }
    }
}
