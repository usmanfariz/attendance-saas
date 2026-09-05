package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Invoice;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.Subscription;
import com.attendance.saas.entity.enums.InvoiceStatus;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Subscription and billing")
class SubscriptionBillingIT extends AbstractIntegrationTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    private String superAdminToken() throws Exception {
        givenUser(null, "root@platform.test", UserRole.SUPER_ADMIN, UserStatus.ACTIVE);
        return bearer(loginAndGetAccessToken("root@platform.test"));
    }

    @Test
    @DisplayName("super admin mengelola katalog plan")
    void planCatalogueManagement() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        String token = superAdminToken();

        String created = mockMvc.perform(post("/api/v1/plans").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "STARTER",
                                  "name": "Starter",
                                  "description": "Paket pemula",
                                  "priceAmount": 149000.00,
                                  "currency": "IDR",
                                  "billingPeriod": "MONTHLY",
                                  "maxEmployees": 25,
                                  "maxLocations": 2,
                                  "maxUsers": 30,
                                  "geofenceIncluded": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("STARTER"))
                .andExpect(jsonPath("$.data.maxEmployees").value(25))
                .andExpect(jsonPath("$.data.subscriberCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long planId = json(created).path("data").path("id").asLong();

        // Duplicate code is refused.
        mockMvc.perform(post("/api/v1/plans").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"starter","name":"Lain","priceAmount":1000,"billingPeriod":"MONTHLY"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLAN_CODE_ALREADY_EXISTS"));

        mockMvc.perform(put("/api/v1/plans/{id}", planId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"STARTER","name":"Starter Plus","priceAmount":199000.00,
                                 "billingPeriod":"MONTHLY","maxEmployees":40,"geofenceIncluded":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Starter Plus"))
                .andExpect(jsonPath("$.data.maxEmployees").value(40))
                // Limits left out become unlimited.
                .andExpect(jsonPath("$.data.maxLocations").doesNotExist());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/plans/{id}", planId).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("plan yang masih dipakai perusahaan tidak dapat dihapus")
    void planInUseCannotBeDeleted() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan plan = givenPlan("BASIC", 50, 3, 75, true);
        givenCompanyOnPlan("alpha", plan);
        String token = superAdminToken();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/plans/{id}", plan.getId()).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PLAN_IN_USE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("nonaktifkan")));
    }

    @Test
    @DisplayName("memindahkan perusahaan ke plan berbayar menerbitkan tagihan pertamanya")
    void assigningAPaidPlanIssuesAnInvoice() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan free = givenPlan("FREE_TIER", 5, 1, 5, false);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", free);
        String token = superAdminToken();

        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/plan", alpha.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":%d}
                                """.formatted(paid.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan.code").value("PRO"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currentPeriodStart").value("2026-09-01"))
                .andExpect(jsonPath("$.data.currentPeriodEnd").value("2026-09-30"));

        Invoice invoice = invoiceRepository.findAll().get(0);
        assertThat(invoice.getPlanCode()).isEqualTo("PRO");
        assertThat(invoice.getAmount()).isEqualByComparingTo(new BigDecimal("299000.00"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getPeriodStart()).isEqualTo(DAY);
        assertThat(invoice.getDueDate()).isEqualTo(DAY.plusDays(14));
    }

    @Test
    @DisplayName("masa trial tidak menerbitkan tagihan tetapi tetap memberi hak penuh")
    void trialGrantsAccessWithoutAnInvoice() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", givenPlan("FREE_TIER", 5, 1, 5, false));
        String token = superAdminToken();

        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/plan", alpha.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":%d,"trialDays":14}
                                """.formatted(paid.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TRIAL"))
                .andExpect(jsonPath("$.data.trialEndsAt").value("2026-09-15"));

        assertThat(invoiceRepository.count()).isZero();

        // A trial still allows provisioning.
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/employees")
                .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"employeeCode":"EMP-001","name":"Siti"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("plan gratis tidak pernah ditagih")
    void freePlansAreNeverInvoiced() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan free = planRepository.save(Plan.builder()
                .code("ZERO").name("Zero").priceAmount(BigDecimal.ZERO).currency("IDR")
                .billingPeriod(com.attendance.saas.entity.enums.BillingPeriod.MONTHLY)
                .active(true).build());
        Company alpha = givenCompanyOnPlan("alpha", givenPlan("TEMP", 5, 1, 5, false));
        String token = superAdminToken();

        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/plan", alpha.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"planId":%d}
                        """.formatted(free.getId())))
                .andExpect(status().isOk());

        assertThat(invoiceRepository.count()).isZero();
    }

    @Test
    @DisplayName("melunasi tagihan mengembalikan langganan PAST_DUE menjadi ACTIVE")
    void payingAnInvoiceRestoresTheSubscription() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", paid);
        Subscription subscription = subscriptionRepository.findByCompanyId(alpha.getId()).orElseThrow();
        String token = superAdminToken();

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .company(alpha)
                .subscription(subscription)
                .invoiceNumber("INV-TEST-1")
                .planCode(paid.getCode())
                .planName(paid.getName())
                .amount(paid.getPriceAmount())
                .currency("IDR")
                .periodStart(DAY.minusMonths(1))
                .periodEnd(DAY.minusDays(1))
                .status(InvoiceStatus.ISSUED)
                .issuedAt(clock.instant())
                .dueDate(DAY.minusDays(3))
                .build());

        // The cycle sweeps the overdue invoice and suspends growth.
        mockMvc.perform(post("/api/v1/invoices/billing-cycle/run").header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(invoiceRepository.findById(invoice.getId()).orElseThrow().getStatus())
                .isEqualTo(InvoiceStatus.OVERDUE);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        mockMvc.perform(post("/api/v1/employees")
                .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"employeeCode":"EMP-001","name":"Siti"}
                        """))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("SUBSCRIPTION_NOT_ACTIVE"));

        mockMvc.perform(put("/api/v1/invoices/{id}/pay", invoice.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentReference":"TRF-20260901-0012"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentReference").value("TRF-20260901-0012"));

        assertThat(subscriptionRepository.findByCompanyId(alpha.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("tagihan yang sudah lunas tidak dapat dilunasi ulang")
    void aPaidInvoiceCannotBePaidTwice() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", paid);
        String token = superAdminToken();

        Invoice invoice = invoiceRepository.save(Invoice.builder()
                .company(alpha)
                .subscription(subscriptionRepository.findByCompanyId(alpha.getId()).orElseThrow())
                .invoiceNumber("INV-TEST-2")
                .planCode(paid.getCode()).planName(paid.getName())
                .amount(paid.getPriceAmount()).currency("IDR")
                .periodStart(DAY).periodEnd(DAY.plusDays(29))
                .status(InvoiceStatus.ISSUED)
                .issuedAt(clock.instant()).dueDate(DAY.plusDays(14))
                .build());

        mockMvc.perform(put("/api/v1/invoices/{id}/pay", invoice.getId()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/invoices/{id}/pay", invoice.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVOICE_ALREADY_SETTLED"));
    }

    @Test
    @DisplayName("siklus penagihan memperpanjang periode dan menerbitkan tagihan berikutnya")
    void billingCycleRenewsAndInvoices() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", paid);
        String token = superAdminToken();

        // Move the clock past the end of the current period.
        clock.setLocalTime(UTC, LocalDate.of(2026, 10, 2), 9, 0);

        mockMvc.perform(post("/api/v1/invoices/billing-cycle/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.renewedSubscriptions").value(1));

        Subscription renewed = subscriptionRepository.findByCompanyId(alpha.getId()).orElseThrow();
        assertThat(renewed.getCurrentPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(renewed.getCurrentPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(invoiceRepository.count()).isEqualTo(1);

        // Running it again changes nothing: the cycle is idempotent.
        mockMvc.perform(post("/api/v1/invoices/billing-cycle/run").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.renewedSubscriptions").value(0));
        assertThat(invoiceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("langganan tanpa perpanjangan otomatis berakhir saat periodenya habis")
    void cancelledSubscriptionExpiresAtPeriodEnd() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", paid);
        String token = superAdminToken();

        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/cancel", alpha.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.autoRenew").value(false))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty());

        clock.setLocalTime(UTC, LocalDate.of(2026, 10, 2), 9, 0);
        mockMvc.perform(post("/api/v1/invoices/billing-cycle/run").header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(subscriptionRepository.findByCompanyId(alpha.getId()).orElseThrow().getStatus())
                .isEqualTo(SubscriptionStatus.EXPIRED);

        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/reactivate", alpha.getId())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.autoRenew").value(true));
    }

    @Test
    @DisplayName("perusahaan hanya melihat tagihannya sendiri")
    void invoicesAreTenantScoped() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", paid);
        Company beta = givenCompanyOnPlan("beta", paid);
        String token = superAdminToken();

        for (Company company : new Company[]{alpha, beta}) {
            invoiceRepository.save(Invoice.builder()
                    .company(company)
                    .subscription(subscriptionRepository.findByCompanyId(company.getId()).orElseThrow())
                    .invoiceNumber("INV-" + company.getCode())
                    .planCode(paid.getCode()).planName(paid.getName())
                    .amount(paid.getPriceAmount()).currency("IDR")
                    .periodStart(DAY).periodEnd(DAY.plusDays(29))
                    .status(InvoiceStatus.ISSUED)
                    .issuedAt(clock.instant()).dueDate(DAY.plusDays(14))
                    .build());
        }

        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/invoices/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].invoiceNumber").value("INV-alpha"));

        // The super admin sees the whole platform.
        mockMvc.perform(get("/api/v1/invoices").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("COMPANY_ADMIN tidak boleh mengelola plan atau langganan perusahaan lain")
    void companyAdminCannotManageBilling() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan paid = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", paid);
        Company beta = givenCompanyOnPlan("beta", paid);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(post("/api/v1/plans").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"HACK","name":"Hack","priceAmount":0,"billingPeriod":"MONTHLY"}
                        """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/subscriptions").header("Authorization", token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/plan", alpha.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"planId":%d}
                        """.formatted(paid.getId())))
                .andExpect(status().isForbidden());

        // Reading another tenant's subscription is refused too.
        mockMvc.perform(get("/api/v1/subscriptions/company/{id}", beta.getId())
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("dashboard sistem merekap tenant, langganan, dan pendapatan")
    void systemDashboard() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Plan pro = givenPlan("PRO", 250, 10, 400, true);
        Company alpha = givenCompanyOnPlan("alpha", pro);
        givenCompanyOnPlan("beta", pro);
        givenEmployee(alpha, "EMP-001", "Siti");
        givenEmployee(alpha, "EMP-002", "Budi");
        String token = superAdminToken();

        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCompanies").value(2))
                .andExpect(jsonPath("$.data.activeCompanies").value(2))
                .andExpect(jsonPath("$.data.totalEmployees").value(2))
                .andExpect(jsonPath("$.data.subscriptionsByStatus.ACTIVE").value(2))
                .andExpect(jsonPath("$.data.subscriptionsByPlan[0].planCode").value("PRO"))
                .andExpect(jsonPath("$.data.subscriptionsByPlan[0].companies").value(2))
                // Two active PRO subscriptions at 299000 each.
                .andExpect(jsonPath("$.data.monthlyRecurringRevenue").value(598000.00))
                .andExpect(jsonPath("$.data.currency").value("IDR"));
    }

    @Test
    @DisplayName("hanya SUPER_ADMIN yang dapat membuka dashboard sistem")
    void systemDashboardIsSuperAdminOnly() throws Exception {
        clock.setLocalTime(UTC, DAY, 9, 0);
        Company alpha = givenCompanyOnPlan("alpha", givenPlan("PRO", 250, 10, 400, true));
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isForbidden());
    }
}
