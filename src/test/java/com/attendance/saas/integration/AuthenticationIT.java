package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Authentication API")
class AuthenticationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("login dengan kredensial benar mengembalikan token dan identitas tenant")
    void loginSucceeds() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@alpha.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = json(body).path("data");
        assertThat(data.path("accessToken").asText()).isNotBlank();
        assertThat(data.path("refreshToken").asText()).isNotBlank();
        assertThat(data.path("user").path("companyId").asLong()).isEqualTo(company.getId());
        assertThat(data.path("user").has("password")).isFalse();
    }

    @Test
    @DisplayName("login dengan password salah mengembalikan 401 tanpa membocorkan detail")
    void loginWithWrongPasswordReturns401() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@alpha.test","password":"WrongPass123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("payload login tidak valid mengembalikan 422 dengan daftar error field")
    void loginValidationFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"bukan-email","password":"123"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("user dari perusahaan yang di-suspend tidak dapat login")
    void suspendedCompanyBlocksLogin() throws Exception {
        Company company = givenCompany("beta", CompanyStatus.SUSPENDED);
        givenUser(company, "admin@beta.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@beta.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_INACTIVE"));
    }

    @Test
    @DisplayName("endpoint terproteksi menolak request tanpa token")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("endpoint terproteksi menolak token yang dimanipulasi")
    void protectedEndpointRejectsTamperedToken() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("admin@alpha.test");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(token.substring(0, token.length() - 4) + "AAAA")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/auth/me mengembalikan profil user beserta perusahaannya")
    void meReturnsProfile() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("hr@alpha.test"))
                .andExpect(jsonPath("$.data.role").value("HR"))
                .andExpect(jsonPath("$.data.companyCode").value("alpha"));
    }

    @Test
    @DisplayName("refresh token dirotasi: token lama tidak dapat dipakai ulang")
    void refreshTokenRotates() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@alpha.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String refreshToken = json(loginBody).path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("logout mencabut refresh token yang beredar")
    void logoutRevokesRefreshTokens() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@alpha.test","password":"%s"}
                                """.formatted(PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = json(loginBody).path("data");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(data.path("accessToken").asText())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(data.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
    }
}
