package com.attendance.saas.mapper;

import com.attendance.saas.dto.billing.InvoiceResponse;
import com.attendance.saas.dto.billing.PlanResponse;
import com.attendance.saas.dto.billing.SubscriptionResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Invoice;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.Subscription;
import org.springframework.stereotype.Component;

@Component
public class BillingMapper {

    public PlanResponse toResponse(Plan plan, Long subscriberCount) {
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPriceAmount(),
                plan.getCurrency(),
                plan.getBillingPeriod(),
                plan.getMaxEmployees(),
                plan.getMaxLocations(),
                plan.getMaxUsers(),
                plan.isGeofenceIncluded(),
                plan.isActive(),
                plan.getSortOrder(),
                subscriberCount,
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    public SubscriptionResponse toResponse(Subscription subscription) {
        Company company = subscription.getCompany();
        return new SubscriptionResponse(
                subscription.getId(),
                company.getId(),
                company.getName(),
                company.getCode(),
                toResponse(subscription.getPlan(), null),
                subscription.getStatus(),
                subscription.getStartDate(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndsAt(),
                subscription.isAutoRenew(),
                subscription.getCancelledAt(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }

    public InvoiceResponse toResponse(Invoice invoice) {
        Company company = invoice.getCompany();
        return new InvoiceResponse(
                invoice.getId(),
                company.getId(),
                company.getName(),
                invoice.getInvoiceNumber(),
                invoice.getPlanCode(),
                invoice.getPlanName(),
                invoice.getPeriodStart(),
                invoice.getPeriodEnd(),
                invoice.getAmount(),
                invoice.getCurrency(),
                invoice.getStatus(),
                invoice.getIssuedAt(),
                invoice.getDueDate(),
                invoice.getPaidAt(),
                invoice.getPaymentReference());
    }
}
