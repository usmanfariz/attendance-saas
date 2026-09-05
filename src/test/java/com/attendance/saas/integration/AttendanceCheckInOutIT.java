package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Check-in and check-out API")
class AttendanceCheckInOutIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 5);
    private static final String EMPTY_BODY = "{}";

    private String checkInBody() {
        return """
                {"latitude":-6.200000,"longitude":106.816666,"photo":"https://cdn.test/in.jpg"}
                """;
    }

    @Test
    @DisplayName("check-in tepat waktu menghasilkan status PRESENT")
    void onTimeCheckInIsPresent() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String email = linkAccount(alpha, employee, "siti@alpha.test");

        clock.setLocalTime(JAKARTA, DAY, 7, 55);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", bearer(loginAndGetAccessToken(email)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.lateMinutes").value(0))
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.checkInLocal").value("07:55:00"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"))
                .andExpect(jsonPath("$.data.shiftName").value("Shift Pagi"));
    }

    @Test
    @DisplayName("check-in melewati toleransi menghasilkan status LATE dengan menit penuh")
    void lateCheckInIsRecorded() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String email = linkAccount(alpha, employee, "siti@alpha.test");

        clock.setLocalTime(JAKARTA, DAY, 8, 40);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", bearer(loginAndGetAccessToken(email)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EMPTY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("LATE"))
                .andExpect(jsonPath("$.data.lateMinutes").value(40));
    }

    @Test
    @DisplayName("check-in dua kali pada shift yang sama ditolak")
    void doubleCheckInIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated());

        clock.setLocalTime(JAKARTA, DAY, 9, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CHECKED_IN"));
    }

    @Test
    @DisplayName("check-out tanpa check-in ditolak")
    void checkOutWithoutCheckInIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 17, 0);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NOT_CHECKED_IN"));
    }

    @Test
    @DisplayName("check-out dua kali ditolak")
    void doubleCheckOutIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY));

        clock.setLocalTime(JAKARTA, DAY, 17, 5);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isOk());

        clock.setLocalTime(JAKARTA, DAY, 17, 30);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CHECKED_OUT"));
    }

    @Test
    @DisplayName("siklus penuh menghitung work_minutes dan early_leave_minutes")
    void fullCycleComputesMinutes() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY));

        clock.setLocalTime(JAKARTA, DAY, 15, 30);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-6.2,"longitude":106.8,"notes":"Pulang lebih awal, izin lisan"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workMinutes").value(450))
                .andExpect(jsonPath("$.data.earlyLeaveMinutes").value(90))
                .andExpect(jsonPath("$.data.checkOutLocal").value("15:30:00"));
    }

    @Test
    @DisplayName("tanpa jadwal dan tanpa shift default, check-in ditolak")
    void checkInWithoutAnyShiftIsRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NO_SHIFT_ASSIGNED"));
    }

    @Test
    @DisplayName("shift default dipakai bila karyawan tidak punya jadwal khusus")
    void defaultShiftIsUsedAsFallback() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Reguler", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        day.setDefaultShift(true);
        shiftRepository.save(day);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 5);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shiftName").value("Shift Reguler"))
                .andExpect(jsonPath("$.data.status").value("PRESENT"));
    }

    @Test
    @DisplayName("karyawan berstatus RESIGNED tidak dapat melakukan absensi")
    void resignedEmployeeCannotCheckIn() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(
                alpha, "EMP-001", "Siti", null, null, EmployeeStatus.RESIGNED);
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_INACTIVE"));
    }

    @Test
    @DisplayName("akun tanpa data karyawan tidak dapat melakukan check-in")
    void accountWithoutEmployeeCannotCheckIn() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NO_EMPLOYEE_PROFILE"));
    }

    @Test
    @DisplayName("/attendance/current menampilkan absensi yang sedang berjalan")
    void currentAttendanceIsExposed() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 6, 0);
        mockMvc.perform(get("/api/v1/attendance/current").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY));

        mockMvc.perform(get("/api/v1/attendance/current").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.checkOut").doesNotExist());
    }

    @Test
    @DisplayName("koordinat dan foto check-in tersimpan")
    void locationAndPhotoArePersisted() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Shift Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        clock.setLocalTime(JAKARTA, DAY, 8, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody()))
                .andExpect(status().isCreated());

        Attendance saved = attendanceRepository.findAll().get(0);
        assertThat(saved.getCheckInLatitude()).isNotNull();
        assertThat(saved.getCheckInLatitude().doubleValue()).isEqualTo(-6.2);
        assertThat(saved.getCheckInPhoto()).isEqualTo("https://cdn.test/in.jpg");
    }

    @Test
    @DisplayName("koordinat di luar rentang valid ditolak 422")
    void invalidCoordinatesRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        String token = bearer(loginAndGetAccessToken(linkAccount(alpha, employee, "siti@alpha.test")));

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":-200,"longitude":500}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    private String linkAccount(Company company, Employee employee, String email) {
        var account = givenUser(company, email, UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(employee);
        userRepository.save(account);
        return email;
    }
}
