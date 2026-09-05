package com.attendance.saas.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the published contract: the document must stay reachable without a
 * token, advertise JWT bearer auth, and cover every area of the API.
 */
@DisplayName("OpenAPI documentation")
class OpenApiDocumentationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("dokumen OpenAPI dapat diakses tanpa autentikasi")
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Attendance SaaS API"))
                .andExpect(jsonPath("$.info.version").value("v1"));
    }

    @Test
    @DisplayName("skema keamanan JWT Bearer terdaftar")
    void bearerAuthIsDeclared() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    @DisplayName("seluruh area API yang diminta spesifikasi terdokumentasi")
    void allRequiredTagsArePresent() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[*].name").value(org.hamcrest.Matchers.hasItems(
                        "Authentication", "Company", "Employee", "Department", "Position",
                        "Shift", "Attendance", "Leave", "Dashboard", "Report",
                        "Plan", "Subscription", "Invoice", "System Admin")));
    }

    @Test
    @DisplayName("tidak ada tag yang terduplikasi")
    void tagsAreNotDuplicated() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = new ArrayList<>();
        json(body).path("tags").forEach(tag -> names.add(tag.path("name").asText()));

        // A tag whose description differs between OpenApiConfig and a
        // controller's @Tag is published twice and shows up as two sections
        // in Swagger UI.
        assertThat(names).doesNotHaveDuplicates();
        assertThat(new HashSet<>(names)).hasSameSizeAs(names);
    }

    @Test
    @DisplayName("endpoint utama tiap fase muncul di dokumen")
    void keyPathsAreDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/employees']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/shifts']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attendance/check-in']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attendance/check-out']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/leave']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attendance-corrections']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/locations']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/dashboard']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/attendance/daily']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reports/attendance/monthly']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/audit-logs']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/plans']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/subscriptions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/invoices']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/dashboard']").exists());
    }
}
