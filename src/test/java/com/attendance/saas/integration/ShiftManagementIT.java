package com.attendance.saas.integration;

import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Shift and roster API")
class ShiftManagementIT extends AbstractIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 7);

    @Test
    @DisplayName("membuat shift malam menandai crossesMidnight dan durasi terjadwal")
    void nightShiftIsFlaggedAsCrossingMidnight() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Shift 3 Malam",
                                  "startTime": "22:00",
                                  "endTime": "07:00",
                                  "lateToleranceMinutes": 15,
                                  "earlyLeaveToleranceMinutes": 10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.crossesMidnight").value(true))
                .andExpect(jsonPath("$.data.scheduledMinutes").value(540))
                .andExpect(jsonPath("$.data.startTime").value("22:00"));

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Shift 1 Pagi","startTime":"06:00","endTime":"15:00"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.crossesMidnight").value(false))
                .andExpect(jsonPath("$.data.scheduledMinutes").value(540));
    }

    @Test
    @DisplayName("shift dengan jam mulai dan selesai sama ditolak")
    void zeroLengthShiftIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nol","startTime":"08:00","endTime":"08:00"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SHIFT"));
    }

    @Test
    @DisplayName("hanya satu shift default per perusahaan")
    void onlyOneDefaultShiftPerCompany() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(post("/api/v1/shifts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Reguler","startTime":"08:00","endTime":"17:00","defaultShift":true}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/shifts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Baru","startTime":"09:00","endTime":"18:00","defaultShift":true}
                        """))
                .andExpect(status().isCreated());

        long defaults = shiftRepository.findAll().stream().filter(Shift::isDefaultShift).count();
        assertThat(defaults).isEqualTo(1);
    }

    @Test
    @DisplayName("shift yang sudah dipakai jadwal tidak dapat dihapus")
    void shiftInUseCannotBeDeleted() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, shift, MONDAY);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/shifts/{id}", shift.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHIFT_IN_USE"));
    }

    @Test
    @DisplayName("shift milik tenant lain tidak terlihat")
    void shiftOfAnotherTenantIsInvisible() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Shift betaShift = givenShift(beta, "Rahasia", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 0);
        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(get("/api/v1/shifts/{id}", betaShift.getId()).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SHIFT_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/shifts").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("penjadwalan rentang tanggal membuat satu entri per hari")
    void assigningARangeCreatesOneEntryPerDay() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        mockMvc.perform(post("/api/v1/employee-shifts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"shiftId":%d,"startDate":"2026-09-07","endDate":"2026-09-11"}
                                """.formatted(employee.getId(), shift.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(5));

        assertThat(employeeShiftRepository.count()).isEqualTo(5);

        mockMvc.perform(get("/api/v1/employee-shifts?startDate=2026-09-07&endDate=2026-09-11")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5));
    }

    @Test
    @DisplayName("penjadwalan ulang ditolak kecuali overwriteExisting=true")
    void reassigningRequiresExplicitOverwrite() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift morning = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        Shift night = givenShift(alpha, "Malam", LocalTime.of(22, 0), LocalTime.of(7, 0), 15, 10);
        givenRoster(alpha, employee, morning, MONDAY);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        String payload = """
                {"employeeId":%d,"shiftId":%d,"startDate":"2026-09-07","endDate":"2026-09-07"%s}
                """;

        mockMvc.perform(post("/api/v1/employee-shifts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.formatted(employee.getId(), night.getId(), "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SHIFT_ASSIGNMENT_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/employee-shifts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.formatted(
                                employee.getId(), night.getId(), ",\"overwriteExisting\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].shiftName").value("Malam"));

        assertThat(employeeShiftRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("rentang tanggal terbalik ditolak")
    void reversedDateRangeIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/employee-shifts")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"shiftId":%d,"startDate":"2026-09-11","endDate":"2026-09-07"}
                                """.formatted(employee.getId(), shift.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("karyawan tenant lain tidak dapat dijadwalkan")
    void cannotRosterAnEmployeeFromAnotherTenant() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Employee betaEmployee = givenEmployee(beta, "EMP-001", "Karyawan Beta");
        Shift alphaShift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/employee-shifts")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"shiftId":%d,"startDate":"2026-09-07","endDate":"2026-09-07"}
                                """.formatted(betaEmployee.getId(), alphaShift.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_NOT_FOUND"));

        assertThat(employeeShiftRepository.count()).isZero();
    }

    @Test
    @DisplayName("EMPLOYEE tidak boleh membuat shift atau menjadwalkan orang lain")
    void employeeRoleCannotManageShifts() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(employee);
        userRepository.save(account);
        String token = bearer(loginAndGetAccessToken("siti@alpha.test"));

        mockMvc.perform(post("/api/v1/shifts")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bikinan Sendiri","startTime":"10:00","endTime":"12:00"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/employee-shifts?startDate=2026-09-07&endDate=2026-09-11")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());

        // Reading their own roster is allowed.
        mockMvc.perform(get("/api/v1/employee-shifts/me?startDate=2026-09-07&endDate=2026-09-11")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }
}
