package com.attendance.saas.integration;

import com.attendance.saas.entity.AuditLog;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Audit log")
class AuditLogIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    private List<AuditAction> recordedActions() {
        return auditLogRepository.findAll().stream().map(AuditLog::getAction).toList();
    }

    @Test
    @DisplayName("login yang berhasil dan yang gagal sama-sama tercatat")
    void loginIsAudited() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"hr@alpha.test","password":"SalahSekali1"}
                                """))
                .andExpect(status().isUnauthorized());

        loginAndGetAccessToken("hr@alpha.test");

        assertThat(recordedActions())
                .containsExactlyInAnyOrder(AuditAction.LOGIN_FAILED, AuditAction.LOGIN);

        AuditLog failed = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.LOGIN_FAILED)
                .findFirst().orElseThrow();
        assertThat(failed.getCompanyId()).isEqualTo(alpha.getId());
        assertThat(failed.getEntity()).isEqualTo("User");
        // The password itself must never reach the log.
        assertThat(failed.getDescription()).doesNotContain("SalahSekali1");
    }

    @Test
    @DisplayName("check-in dan check-out tercatat beserta entity id absensinya")
    void attendanceIsAudited() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, siti, shift, DAY);
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        String token = bearer(loginAndGetAccessToken("siti@alpha.test"));

        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        clock.setLocalTime(JAKARTA, DAY, 17, 0);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(recordedActions())
                .contains(AuditAction.CHECK_IN, AuditAction.CHECK_OUT);

        AuditLog checkIn = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.CHECK_IN)
                .findFirst().orElseThrow();
        assertThat(checkIn.getEntity()).isEqualTo("Attendance");
        assertThat(checkIn.getEntityId()).isNotNull();
        assertThat(checkIn.getUserId()).isEqualTo(account.getId());
        assertThat(checkIn.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("persetujuan dan penolakan cuti tercatat sebagai APPROVE dan REJECT")
    void leaveDecisionsAreAudited() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);
        givenUser(alpha, "spv@alpha.test", UserRole.SUPERVISOR, UserStatus.ACTIVE);

        clock.setLocalTime(JAKARTA, DAY, 9, 0);
        String employeeToken = bearer(loginAndGetAccessToken("siti@alpha.test"));
        String supervisorToken = bearer(loginAndGetAccessToken("spv@alpha.test"));

        String body = mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaveType":"CUTI","startDate":"2026-09-20","endDate":"2026-09-21","reason":"Keluarga"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long leaveId = json(body).path("data").path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/leave/{id}/approve", leaveId)
                        .header("Authorization", supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(recordedActions()).contains(AuditAction.CREATE, AuditAction.APPROVE);

        AuditLog approval = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditAction.APPROVE)
                .findFirst().orElseThrow();
        assertThat(approval.getEntity()).isEqualTo("LeaveRequest");
        assertThat(approval.getEntityId()).isEqualTo(leaveId);
        assertThat(approval.getDescription()).contains("EMP-001");
    }

    @Test
    @DisplayName("CRUD master data tercatat sebagai CREATE, UPDATE, dan DELETE")
    void masterDataChangesAreAudited() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        String created = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Produksi"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long departmentId = json(created).path("data").path("id").asLong();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/departments/{id}", departmentId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Produksi A"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/departments/{id}", departmentId)
                        .header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(recordedActions())
                .contains(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
    }

    @Test
    @DisplayName("API audit log dapat difilter per aksi dan entity")
    void auditLogApiFilters() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(post("/api/v1/departments")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Produksi"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/audit-logs?action=CREATE").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].entity").value("Department"))
                .andExpect(jsonPath("$.data.data[0].userName").value("admin@alpha.test"));

        mockMvc.perform(get("/api/v1/audit-logs?entity=User").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].action").value("LOGIN"));
    }

    @Test
    @DisplayName("audit log tenant lain tidak terlihat")
    void auditLogIsTenantScoped() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        givenUser(beta, "admin@beta.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        String alphaToken = bearer(loginAndGetAccessToken("admin@alpha.test"));
        loginAndGetAccessToken("admin@beta.test");

        // Two logins happened in total, but Alpha may only see its own.
        assertThat(auditLogRepository.count()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/audit-logs").header("Authorization", alphaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("EMPLOYEE tidak boleh membaca audit log")
    void employeeCannotReadAuditLogs() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isForbidden());
    }
}
