package com.attendance.saas.service;

import com.attendance.saas.dto.billing.InvoiceResponse;
import com.attendance.saas.dto.billing.MarkInvoicePaidRequest;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.Invoice;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.Subscription;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.InvoiceStatus;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.BillingMapper;
import com.attendance.saas.repository.InvoiceRepository;
import com.attendance.saas.repository.SubscriptionRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Invoicing and the renewal cycle.
 *
 * <p><strong>No payment gateway is integrated.</strong> This is the billing
 * architecture, not a payment processor: invoices are issued, tracked, and
 * settled by an operator recording the reference from whatever channel took the
 * money. {@link #markPaid} is the single seam a real provider would call from
 * its webhook — everything downstream of it already works.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    /** Days a tenant has to settle an invoice before it counts as overdue. */
    private static final int PAYMENT_TERM_DAYS = 14;

    private final InvoiceRepository invoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingMapper billingMapper;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Issues the invoice for a subscription's current period.
     *
     * <p>Free plans are never invoiced, and the unique constraint on
     * (subscription, period start) means a repeated call cannot double-bill.
     *
     * @return the invoice, or empty when nothing was billable
     */
    @Transactional
    public Optional<Invoice> issueInvoiceForCurrentPeriod(Subscription subscription) {
        Plan plan = subscription.getPlan();

        if (plan.isFree() || subscription.getStatus() == SubscriptionStatus.TRIAL) {
            return Optional.empty();
        }
        if (invoiceRepository.existsBySubscription_IdAndPeriodStart(
                subscription.getId(), subscription.getCurrentPeriodStart())) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        LocalDate issueDate = now.atZone(ZoneId.of("UTC")).toLocalDate();

        Invoice invoice = Invoice.builder()
                .company(subscription.getCompany())
                .subscription(subscription)
                .invoiceNumber(nextInvoiceNumber(subscription, issueDate))
                // Frozen at issue time: repricing the plan must not rewrite this.
                .planCode(plan.getCode())
                .planName(plan.getName())
                .amount(plan.getPriceAmount())
                .currency(plan.getCurrency())
                .periodStart(subscription.getCurrentPeriodStart())
                .periodEnd(subscription.getCurrentPeriodEnd())
                .status(InvoiceStatus.ISSUED)
                .issuedAt(now)
                .dueDate(issueDate.plusDays(PAYMENT_TERM_DAYS))
                .build();
        invoiceRepository.save(invoice);

        log.info("Invoice {} issued for company {} ({} {})",
                invoice.getInvoiceNumber(), subscription.getCompanyId(),
                invoice.getCurrency(), invoice.getAmount());
        return Optional.of(invoice);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> searchAll(Long companyId,
                                                   InvoiceStatus status,
                                                   Pageable pageable) {
        return PageResponse.of(
                invoiceRepository.search(companyId, status, pageable), billingMapper::toResponse);
    }

    /** Invoices of the caller's own tenant. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> myInvoices(Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        return PageResponse.of(
                invoiceRepository.findByCompany_IdOrderByIssuedAtDesc(companyId, pageable),
                billingMapper::toResponse);
    }

    /**
     * Settles an invoice and lifts the tenant out of {@code PAST_DUE}.
     *
     * <p>This is where a payment provider's webhook would land.
     */
    @Transactional
    public InvoiceResponse markPaid(Long invoiceId, MarkInvoicePaidRequest request) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.INVOICE_NOT_FOUND, "Tagihan tidak ditemukan"));

        if (invoice.getStatus().isSettled()) {
            throw new ConflictException(
                    ErrorCode.INVOICE_ALREADY_SETTLED,
                    "Tagihan sudah berstatus " + invoice.getStatus());
        }

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(clock.instant());
        invoice.setPaymentReference(
                StringUtils.hasText(request.paymentReference())
                        ? request.paymentReference().trim() : null);

        restoreSubscriptionIfSettled(invoice);

        auditService.record(AuditAction.UPDATE, "Invoice", invoiceId,
                "Menandai tagihan %s lunas".formatted(invoice.getInvoiceNumber()));
        log.info("Invoice {} marked paid", invoice.getInvoiceNumber());
        return billingMapper.toResponse(invoice);
    }

    @Transactional
    public InvoiceResponse voidInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.INVOICE_NOT_FOUND, "Tagihan tidak ditemukan"));

        if (invoice.getStatus().isSettled()) {
            throw new ConflictException(
                    ErrorCode.INVOICE_ALREADY_SETTLED,
                    "Tagihan sudah berstatus " + invoice.getStatus());
        }

        invoice.setStatus(InvoiceStatus.VOID);
        auditService.record(AuditAction.UPDATE, "Invoice", invoiceId,
                "Membatalkan tagihan " + invoice.getInvoiceNumber());
        return billingMapper.toResponse(invoice);
    }

    /**
     * Advances every subscription whose period has ended and bills the new one.
     * Idempotent, so it is safe to run repeatedly — the intended trigger is a
     * daily scheduler or an operator call.
     *
     * @return how many subscriptions were advanced
     */
    @Transactional
    public int runBillingCycle() {
        LocalDate today = clock.instant().atZone(ZoneId.of("UTC")).toLocalDate();

        List<Invoice> overdue = invoiceRepository.findOverdue(today);
        overdue.forEach(invoice -> {
            invoice.setStatus(InvoiceStatus.OVERDUE);
            markSubscriptionPastDue(invoice);
        });

        List<Subscription> due = subscriptionRepository.findDueForRenewal(today);
        int renewed = 0;

        for (Subscription subscription : due) {
            // A subscription that will not renew simply ends when its period does.
            if (!subscription.isAutoRenew()
                    || subscription.getStatus() == SubscriptionStatus.CANCELLED) {
                subscription.setStatus(SubscriptionStatus.EXPIRED);
                continue;
            }
            if (subscription.isTrialExpired(today)) {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
            }

            LocalDate nextStart = subscription.getCurrentPeriodEnd().plusDays(1);
            subscription.setCurrentPeriodStart(nextStart);
            subscription.setCurrentPeriodEnd(
                    subscription.getPlan().getBillingPeriod().endOfPeriod(nextStart));
            issueInvoiceForCurrentPeriod(subscription);
            renewed++;
        }

        log.info("Billing cycle: {} overdue invoice(s), {} subscription(s) renewed",
                overdue.size(), renewed);
        return renewed;
    }

    private void markSubscriptionPastDue(Invoice invoice) {
        Subscription subscription = invoice.getSubscription();
        if (subscription != null && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            log.info("Company {} marked PAST_DUE: invoice {} overdue",
                    subscription.getCompanyId(), invoice.getInvoiceNumber());
        }
    }

    private void restoreSubscriptionIfSettled(Invoice invoice) {
        Subscription subscription = invoice.getSubscription();
        if (subscription != null && subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            log.info("Company {} restored to ACTIVE after payment", subscription.getCompanyId());
        }
    }

    /** {@code INV-<yyyyMM>-<companyId>-<periodStart day>} — readable and unique. */
    private String nextInvoiceNumber(Subscription subscription, LocalDate issueDate) {
        return "INV-%d%02d-%d-%02d".formatted(
                issueDate.getYear(),
                issueDate.getMonthValue(),
                subscription.getCompanyId(),
                subscription.getCurrentPeriodStart().getDayOfYear());
    }
}
