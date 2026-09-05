package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The core SaaS guarantee: knowing another tenant's id must not be enough to
 * read or modify its data.
 */
@DisplayName("Multi-tenant isolation")
class MultiTenantIsolationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("admin Company A tidak dapat membaca data Company B walau tahu id-nya")
    void adminCannotReadForeignCompany() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        String token = loginAndGetAccessToken("admin@alpha.test");

        mockMvc.perform(get("/api/v1/companies/{id}", alpha.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("alpha"));

        mockMvc.perform(get("/api/v1/companies/{id}", beta.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CROSS_TENANT_ACCESS_DENIED"));
    }

    @Test
    @DisplayName("admin Company A tidak dapat mengubah data Company B")
    void adminCannotUpdateForeignCompany() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/companies/{id}", beta.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Dibajak",
                                  "email": "hacker@alpha.test",
                                  "timezone": "Asia/Jakarta"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/companies/me selalu mengikuti tenant di dalam token")
    void currentCompanyFollowsToken() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        givenUser(beta, "hr@beta.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/companies/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("alpha"));

        mockMvc.perform(get("/api/v1/companies/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@beta.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("beta"));
    }

    @Test
    @DisplayName("role EMPLOYEE tidak dapat mengubah profil perusahaan")
    void employeeCannotUpdateCompany() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "staff@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);

        mockMvc.perform(put("/api/v1/companies/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("staff@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"x@alpha.test","timezone":"Asia/Jakarta"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("COMPANY_ADMIN tidak dapat melihat daftar seluruh tenant")
    void companyAdminCannotListAllTenants() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/companies")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SUPER_ADMIN dapat melihat seluruh tenant dan mengubah statusnya")
    void superAdminManagesAllTenants() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(null, "root@attendance.test", UserRole.SUPER_ADMIN, UserStatus.ACTIVE);

        String token = loginAndGetAccessToken("root@attendance.test");

        mockMvc.perform(get("/api/v1/companies?page=0&size=10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));

        mockMvc.perform(patch("/api/v1/companies/{id}/status", alpha.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("SUPER_ADMIN tidak punya tenant sendiri sehingga /companies/me ditolak")
    void superAdminHasNoOwnCompany() throws Exception {
        givenUser(null, "root@attendance.test", UserRole.SUPER_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/companies/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("root@attendance.test"))))
                .andExpect(status().isForbidden());
    }
}
