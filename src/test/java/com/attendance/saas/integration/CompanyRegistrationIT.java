package com.attendance.saas.integration;

import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Company registration API")
class CompanyRegistrationIT extends AbstractIntegrationTest {

    private static final String VALID_PAYLOAD = """
            {
              "companyName": "PT Sumber Makmur",
              "companyCode": "sumber-makmur",
              "companyEmail": "info@sumbermakmur.test",
              "phone": "+628123456789",
              "address": "Jl. Merdeka 1",
              "timezone": "Asia/Makassar",
              "adminName": "Budi Santoso",
              "adminEmail": "budi@sumbermakmur.test",
              "adminPassword": "Password123!"
            }
            """;

    @Test
    @DisplayName("registrasi membuat tenant beserta admin pertamanya dan langsung bisa login")
    void registrationCreatesTenantAndAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.company.code").value("sumber-makmur"))
                .andExpect(jsonPath("$.data.company.timezone").value("Asia/Makassar"))
                .andExpect(jsonPath("$.data.company.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.adminEmail").value("budi@sumbermakmur.test"));

        User admin = userRepository.findByEmailIgnoreCase("budi@sumbermakmur.test").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(UserRole.COMPANY_ADMIN);
        assertThat(admin.getCompanyId()).isNotNull();
        assertThat(admin.getPassword()).startsWith("$2a$").isNotEqualTo("Password123!");
        assertThat(companyRepository.findByCodeIgnoreCase("sumber-makmur").orElseThrow().getStatus())
                .isEqualTo(CompanyStatus.ACTIVE);

        assertThat(loginAndGetAccessToken("budi@sumbermakmur.test")).isNotBlank();
    }

    @Test
    @DisplayName("kode perusahaan duplikat ditolak dengan 409")
    void duplicateCompanyCodeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PAYLOAD)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace(
                                "\"adminEmail\": \"budi@sumbermakmur.test\"",
                                "\"adminEmail\": \"lain@sumbermakmur.test\"")
                                .replace(
                                "\"companyEmail\": \"info@sumbermakmur.test\"",
                                "\"companyEmail\": \"lain@sumbermakmur.test\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMPANY_CODE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("email admin yang sudah dipakai tenant lain ditolak dengan 409")
    void duplicateAdminEmailRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-company")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_PAYLOAD)).andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD
                                .replace("\"companyCode\": \"sumber-makmur\"", "\"companyCode\": \"lain\"")
                                .replace("\"companyEmail\": \"info@sumbermakmur.test\"",
                                        "\"companyEmail\": \"info@lain.test\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("password lemah ditolak sebelum tenant dibuat")
    void weakPasswordRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"Password123!\"", "\"password\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        assertThat(companyRepository.count()).isZero();
    }

    @Test
    @DisplayName("timezone tidak dikenal ditolak")
    void invalidTimezoneRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-company")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD.replace("\"Asia/Makassar\"", "\"Mars/Olympus\"")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TIMEZONE"));

        assertThat(companyRepository.count()).isZero();
    }
}
