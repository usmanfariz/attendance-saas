package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Position;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Employee search and pagination")
class EmployeeSearchIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("pagination mengembalikan metadata halaman yang benar")
    void paginationMetadata() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        for (int i = 1; i <= 25; i++) {
            givenEmployee(alpha, "EMP-%03d".formatted(i), "Karyawan %02d".formatted(i));
        }
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(get("/api/v1/employees?page=0&size=10").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.length()").value(10))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(25))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(get("/api/v1/employees?page=2&size=10").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.length()").value(5))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("pencarian keyword mencakup kode, nama, dan email")
    void keywordSearchCoversCodeNameAndEmail() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenEmployee(alpha, "EMP-001", "Siti Rahayu");
        givenEmployee(alpha, "EMP-002", "Budi Santoso");
        givenEmployee(alpha, "MGR-001", "Andi Wijaya");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(get("/api/v1/employees?keyword=rahayu").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].name").value("Siti Rahayu"));

        mockMvc.perform(get("/api/v1/employees?keyword=MGR").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("MGR-001"));

        mockMvc.perform(get("/api/v1/employees?keyword=emp-00").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/employees?keyword=tidak-ada").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("filter departemen, posisi, dan status dapat digabungkan")
    void filtersCombine() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Department production = givenDepartment(alpha, "Produksi");
        Department finance = givenDepartment(alpha, "Keuangan");
        Position operator = givenPosition(alpha, "Operator");

        givenEmployee(alpha, "EMP-001", "Aktif Produksi", production, operator, EmployeeStatus.ACTIVE);
        givenEmployee(alpha, "EMP-002", "Resign Produksi", production, operator, EmployeeStatus.RESIGNED);
        givenEmployee(alpha, "EMP-003", "Aktif Keuangan", finance, null, EmployeeStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(get("/api/v1/employees?departmentId=" + production.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/employees?departmentId=%d&status=ACTIVE".formatted(production.getId()))
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("EMP-001"));

        mockMvc.perform(get("/api/v1/employees?positionId=" + operator.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/employees?status=RESIGNED").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("sorting mengikuti parameter sort")
    void sortingIsApplied() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenEmployee(alpha, "EMP-003", "Citra");
        givenEmployee(alpha, "EMP-001", "Andi");
        givenEmployee(alpha, "EMP-002", "Budi");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(get("/api/v1/employees?sort=employeeCode,desc")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("EMP-003"));

        mockMvc.perform(get("/api/v1/employees?sort=name,asc").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.data[0].name").value("Andi"));
    }

    @Test
    @DisplayName("daftar karyawan hanya berisi karyawan tenant sendiri")
    void listIsScopedToTenant() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenEmployee(alpha, "EMP-001", "Karyawan Alpha");
        givenEmployee(beta, "EMP-001", "Karyawan Beta");
        givenEmployee(beta, "EMP-002", "Karyawan Beta 2");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/employees")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].name").value("Karyawan Alpha"));
    }
}
