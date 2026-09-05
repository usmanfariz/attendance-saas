package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Employee;
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

@DisplayName("Employee CRUD API")
class EmployeeCrudIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("siklus penuh: create, read, update, dan soft delete")
    void fullCrudCycle() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Department production = givenDepartment(alpha, "Produksi");
        Position operator = givenPosition(alpha, "Operator Mesin");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        String created = mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "employeeCode": "EMP-001",
                                  "name": "Siti Rahayu",
                                  "email": "siti@alpha.test",
                                  "phone": "+628111222333",
                                  "departmentId": %d,
                                  "positionId": %d,
                                  "joinDate": "2026-01-15"
                                }
                                """.formatted(production.getId(), operator.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.data.departmentName").value("Produksi"))
                .andExpect(jsonPath("$.data.positionName").value("Operator Mesin"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.hasUserAccount").value(false))
                .andReturn().getResponse().getContentAsString();

        long employeeId = json(created).path("data").path("id").asLong();

        mockMvc.perform(get("/api/v1/employees/{id}", employeeId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Siti Rahayu"));

        mockMvc.perform(put("/api/v1/employees/{id}", employeeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Siti Rahayu Wijaya",
                                  "email": "siti.w@alpha.test",
                                  "phone": "+628111222444",
                                  "departmentId": null,
                                  "positionId": %d,
                                  "joinDate": "2026-01-15",
                                  "status": "ACTIVE"
                                }
                                """.formatted(operator.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Siti Rahayu Wijaya"))
                .andExpect(jsonPath("$.data.departmentId").doesNotExist())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-001"));

        mockMvc.perform(delete("/api/v1/employees/{id}", employeeId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESIGNED"));

        // Soft delete: the row survives so attendance history keeps its owner.
        assertThat(employeeRepository.findById(employeeId)).isPresent();
    }

    @Test
    @DisplayName("kode karyawan duplikat dalam satu perusahaan ditolak 409")
    void duplicateCodeInSameCompanyRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenEmployee(alpha, "EMP-001", "Siti");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeCode":"EMP-001","name":"Orang Lain"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_CODE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("kode karyawan yang sama boleh dipakai perusahaan berbeda")
    void sameCodeAllowedAcrossCompanies() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenEmployee(alpha, "EMP-001", "Siti");
        givenUser(beta, "hr@beta.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@beta.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeCode":"EMP-001","name":"Karyawan Beta"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("payload karyawan tidak valid ditolak 422")
    void invalidPayloadRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeCode":"EMP 001!","name":"","email":"bukan-email"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("EMPLOYEE tidak boleh membuat atau melihat daftar karyawan")
    void employeeRoleCannotManageEmployees() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee staff = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(staff);
        userRepository.save(account);

        String token = loginAndGetAccessToken("siti@alpha.test");

        mockMvc.perform(get("/api/v1/employees").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/employees")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeCode":"EMP-999","name":"Selundupan"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/employees/me mengembalikan profil karyawan pemilik akun")
    void employeeCanReadOwnProfile() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Department production = givenDepartment(alpha, "Produksi");
        Employee staff = givenEmployee(
                alpha, "EMP-001", "Siti", production, null, EmployeeStatus.ACTIVE);
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(staff);
        userRepository.save(account);

        mockMvc.perform(get("/api/v1/employees/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.data.departmentName").value("Produksi"));
    }

    @Test
    @DisplayName("/employees/me menolak akun yang tidak terhubung ke data karyawan")
    void accountWithoutEmployeeProfileIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/employees/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NO_EMPLOYEE_PROFILE"));
    }

    @Test
    @DisplayName("HR dapat membuatkan akun login untuk karyawan, tapi tidak role COMPANY_ADMIN")
    void createEmployeeAccount() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee staff = givenEmployee(alpha, "EMP-001", "Siti");
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = loginAndGetAccessToken("hr@alpha.test");

        mockMvc.perform(post("/api/v1/employees/{id}/account", staff.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"siti@alpha.test","password":"Password123!","role":"EMPLOYEE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("EMPLOYEE"));

        // Second attempt on the same employee is a conflict.
        mockMvc.perform(post("/api/v1/employees/{id}/account", staff.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"siti2@alpha.test","password":"Password123!","role":"EMPLOYEE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_ALREADY_HAS_ACCOUNT"));

        Employee other = givenEmployee(alpha, "EMP-002", "Budi");
        mockMvc.perform(post("/api/v1/employees/{id}/account", other.getId())
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"budi@alpha.test","password":"Password123!","role":"COMPANY_ADMIN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ROLE_NOT_GRANTABLE"));
    }
}
