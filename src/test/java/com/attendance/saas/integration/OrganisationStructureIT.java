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
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Department and Position API")
class OrganisationStructureIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("CRUD departemen berjalan dan menampilkan jumlah karyawan")
    void departmentCrud() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("admin@alpha.test");

        String created = mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Produksi","description":"Lini produksi utama"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Produksi"))
                .andExpect(jsonPath("$.data.employeeCount").value(0))
                .andReturn().getResponse().getContentAsString();

        long departmentId = json(created).path("data").path("id").asLong();

        mockMvc.perform(put("/api/v1/departments/{id}", departmentId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Produksi A","description":"Shift pagi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Produksi A"));

        mockMvc.perform(get("/api/v1/departments/all").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/api/v1/departments/{id}", departmentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        assertThat(departmentRepository.count()).isZero();
    }

    @Test
    @DisplayName("nama departemen duplikat dalam satu tenant ditolak, beda tenant diizinkan")
    void departmentNameUniquePerTenant() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenDepartment(alpha, "Produksi");
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        givenUser(beta, "admin@beta.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"produksi"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEPARTMENT_NAME_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@beta.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Produksi"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("departemen yang masih dipakai karyawan tidak dapat dihapus")
    void departmentInUseCannotBeDeleted() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Department production = givenDepartment(alpha, "Produksi");
        givenEmployee(alpha, "EMP-001", "Siti", production, null, EmployeeStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/departments/{id}", production.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DEPARTMENT_IN_USE"));

        assertThat(departmentRepository.findById(production.getId())).isPresent();
    }

    @Test
    @DisplayName("departemen tenant lain tidak dapat dibaca maupun diubah")
    void departmentOfAnotherTenantIsInvisible() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Department betaDepartment = givenDepartment(beta, "Rahasia Beta");
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("admin@alpha.test");

        mockMvc.perform(get("/api/v1/departments/{id}", betaDepartment.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEPARTMENT_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/departments/{id}", betaDepartment.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Dibajak"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/departments/{id}", betaDepartment.getId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("karyawan tidak dapat memakai departemen milik tenant lain")
    void employeeCannotReferenceForeignDepartment() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Department betaDepartment = givenDepartment(beta, "Produksi Beta");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeCode":"EMP-001","name":"Siti","departmentId":%d}
                                """.formatted(betaDepartment.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DEPARTMENT_NOT_FOUND"));

        assertThat(employeeRepository.count()).isZero();
    }

    @Test
    @DisplayName("CRUD posisi berjalan dan pencarian posisi difilter per tenant")
    void positionCrudAndSearch() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenPosition(beta, "Operator Beta");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(post("/api/v1/positions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Operator Mesin","description":"Menjalankan mesin produksi"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/positions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Staf Administrasi"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/positions?keyword=operator").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].name").value("Operator Mesin"));

        mockMvc.perform(get("/api/v1/positions").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("posisi yang masih dipakai karyawan tidak dapat dihapus")
    void positionInUseCannotBeDeleted() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Position operator = givenPosition(alpha, "Operator");
        givenEmployee(alpha, "EMP-001", "Siti", null, operator, EmployeeStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/positions/{id}", operator.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("POSITION_IN_USE"));
    }

    @Test
    @DisplayName("HR tidak boleh menghapus departemen, hanya COMPANY_ADMIN")
    void onlyCompanyAdminCanDelete() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Department department = givenDepartment(alpha, "Produksi");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/departments/{id}", department.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isForbidden());
    }
}
