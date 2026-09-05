package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plan limits are what turn a subscription into something enforceable, so each
 * one is exercised at its boundary.
 */
@DisplayName("Plan limits")
class PlanLimitIT extends AbstractIntegrationTest {

    private String employeePayload(String code) {
        return """
                {"employeeCode":"%s","name":"Karyawan %s"}
                """.formatted(code, code);
    }

    @Test
    @DisplayName("batas karyawan menolak penambahan setelah kuota penuh")
    void employeeLimitBlocksGrowth() throws Exception {
        Plan small = givenPlan("SMALL", 2, 1, 10, false);
        Company alpha = givenCompanyOnPlan("alpha", small);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        mockMvc.perform(post("/api/v1/employees").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(employeePayload("EMP-001")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/employees").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(employeePayload("EMP-002")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/employees").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(employeePayload("EMP-003")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("upgrade")));

        // The rejected request must leave nothing behind.
        assertThat(employeeRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("batas lokasi dan akun pengguna juga ditegakkan")
    void locationAndUserLimits() throws Exception {
        Plan small = givenPlan("SMALL", 10, 1, 2, true);
        Company alpha = givenCompanyOnPlan("alpha", small);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        Employee budi = givenEmployee(alpha, "EMP-002", "Budi");
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        String location = """
                {"name":"%s","latitude":-6.17,"longitude":106.82,"radiusMeter":150}
                """;

        mockMvc.perform(post("/api/v1/locations").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(location.formatted("Kantor Pusat")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/locations").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(location.formatted("Cabang")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("LOCATION_LIMIT_REACHED"));

        // One user already exists (the admin), so the second fills the quota.
        String account = """
                {"email":"%s","password":"Password123!","role":"EMPLOYEE"}
                """;
        mockMvc.perform(post("/api/v1/employees/{id}/account", siti.getId())
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(account.formatted("siti@alpha.test")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/employees/{id}/account", budi.getId())
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(account.formatted("budi@alpha.test")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("USER_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("plan tanpa batas tidak pernah menghalangi")
    void unlimitedPlanNeverBlocks() throws Exception {
        Plan unlimited = givenPlan("UNLIMITED", null, null, null, true);
        Company alpha = givenCompanyOnPlan("alpha", unlimited);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/employees").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(employeePayload("EMP-%03d".formatted(i))))
                    .andExpect(status().isCreated());
        }
        assertThat(employeeRepository.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("geofencing hanya dapat dinyalakan bila termasuk dalam plan")
    void geofenceRequiresTheEntitlement() throws Exception {
        Plan withoutGeofence = givenPlan("BASIC_NO_GEO", 10, 5, 10, false);
        Company alpha = givenCompanyOnPlan("alpha", withoutGeofence);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(put("/api/v1/locations/settings").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"geofenceEnabled":true}
                                """))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("FEATURE_NOT_IN_PLAN"));

        // Turning it off is always allowed, whatever the plan.
        mockMvc.perform(put("/api/v1/locations/settings").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"geofenceEnabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.geofenceEnabled").value(false));
    }

    @Test
    @DisplayName("langganan yang dibatalkan tidak dapat menambah data baru")
    void cancelledSubscriptionBlocksProvisioning() throws Exception {
        Plan plan = givenPlan("BASIC", 100, 5, 100, true);
        Company alpha = givenCompanyOnPlan("alpha", plan);
        subscriptionRepository.findByCompanyId(alpha.getId()).ifPresent(subscription -> {
            subscription.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(subscription);
        });
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        mockMvc.perform(post("/api/v1/employees").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(employeePayload("EMP-001")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("SUBSCRIPTION_NOT_ACTIVE"));

        // Reading existing data still works: cancellation stops growth, not access.
        mockMvc.perform(get("/api/v1/employees").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("turun plan tidak menghapus data yang sudah ada, hanya menghentikan penambahan")
    void downgradingKeepsExistingData() throws Exception {
        Plan big = givenPlan("BIG", 100, 10, 100, true);
        Plan tiny = givenPlan("TINY", 1, 1, 10, false);
        Company alpha = givenCompanyOnPlan("alpha", big);

        givenEmployee(alpha, "EMP-001", "Siti");
        givenEmployee(alpha, "EMP-002", "Budi");
        givenEmployee(alpha, "EMP-003", "Andi");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        givenUser(null, "root@platform.test", UserRole.SUPER_ADMIN, UserStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/subscriptions/company/{id}/plan", alpha.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("root@platform.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"planId":%d}
                                """.formatted(tiny.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan.code").value("TINY"));

        // The three employees survive the downgrade.
        assertThat(employeeRepository.countByCompany_Id(alpha.getId())).isEqualTo(3);

        // But the tenant is now over its limit and cannot add a fourth.
        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON).content(employeePayload("EMP-004")))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("/subscriptions/me menampilkan pemakaian terhadap batas plan")
    void usageIsVisibleToTheCompanyAdmin() throws Exception {
        Plan plan = givenPlan("BASIC", 50, 3, 75, true);
        Company alpha = givenCompanyOnPlan("alpha", plan);
        givenEmployee(alpha, "EMP-001", "Siti");
        givenEmployee(alpha, "EMP-002", "Budi");
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.plan.code").value("BASIC"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.employees.used").value(2))
                .andExpect(jsonPath("$.data.employees.limit").value(50))
                .andExpect(jsonPath("$.data.employees.remaining").value(48))
                .andExpect(jsonPath("$.data.employees.unlimited").value(false))
                .andExpect(jsonPath("$.data.users.used").value(1))
                .andExpect(jsonPath("$.data.geofenceAvailable").value(true));
    }

    @Test
    @DisplayName("pemakaian pada plan tanpa batas ditandai unlimited")
    void unlimitedUsageIsFlagged() throws Exception {
        Plan plan = givenPlan("ENTERPRISE", null, null, null, true);
        Company alpha = givenCompanyOnPlan("alpha", plan);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employees.unlimited").value(true))
                .andExpect(jsonPath("$.data.employees.limit").doesNotExist())
                .andExpect(jsonPath("$.data.employees.remaining").doesNotExist());
    }

    @Test
    @DisplayName("registrasi mandiri langsung mendapat langganan FREE")
    void selfServiceRegistrationGetsTheFreePlan() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "PT Baru",
                                  "companyCode": "baru",
                                  "companyEmail": "info@baru.test",
                                  "adminName": "Admin Baru",
                                  "adminEmail": "admin@baru.test",
                                  "adminPassword": "Password123!"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/subscriptions/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@baru.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.plan.code").value("FREE"))
                .andExpect(jsonPath("$.data.subscription.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.geofenceAvailable").value(false));
    }
}
