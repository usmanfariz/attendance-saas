package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The night shift is the case that breaks naive attendance systems: the shift
 * starts on one calendar day and ends on the next, so a single working period
 * must stay a single row dated on the day it began.
 */
@DisplayName("Night shift attendance")
class NightShiftAttendanceIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate NIGHT = LocalDate.of(2026, 9, 5);
    private static final LocalDate NEXT_MORNING = NIGHT.plusDays(1);
    private static final String EMPTY_BODY = "{}";

    private Company alpha;
    private Employee employee;
    private String token;

    private void givenNightShiftRoster() throws Exception {
        alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        employee = givenEmployee(alpha, "EMP-001", "Joko");
        Shift night = givenShift(alpha, "Shift 3 Malam", LocalTime.of(22, 0), LocalTime.of(7, 0), 15, 10);
        givenRoster(alpha, employee, night, NIGHT);
        givenRoster(alpha, employee, night, NEXT_MORNING);

        var account = givenUser(alpha, "joko@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(employee);
        userRepository.save(account);
        token = bearer(loginAndGetAccessToken("joko@alpha.test"));
    }

    @Test
    @DisplayName("check-in 22:00 dan check-out 07:00 esok hari tercatat sebagai satu absensi")
    void nightShiftSpansTwoCalendarDaysAsOneRecord() throws Exception {
        givenNightShiftRoster();

        clock.setLocalTime(JAKARTA, NIGHT, 21, 55);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.lateMinutes").value(0));

        // Next calendar day, 07:00 — the shift that started last night ends now.
        clock.setLocalTime(JAKARTA, NEXT_MORNING, 7, 0);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.workMinutes").value(545))
                .andExpect(jsonPath("$.data.earlyLeaveMinutes").value(0))
                .andExpect(jsonPath("$.data.checkOutLocal").value("07:00:00"));

        assertThat(attendanceRepository.count()).isEqualTo(1);
        Attendance saved = attendanceRepository.findAll().get(0);
        assertThat(saved.getAttendanceDate()).isEqualTo(NIGHT);
        assertThat(saved.getCheckIn().atZone(JAKARTA).toLocalDate()).isEqualTo(NIGHT);
        assertThat(saved.getCheckOut().atZone(JAKARTA).toLocalDate()).isEqualTo(NEXT_MORNING);
    }

    @Test
    @DisplayName("check-in 00:30 dini hari tetap masuk tanggal shift dimulai, bukan hari kalender")
    void checkInAfterMidnightBelongsToTheStartingDate() throws Exception {
        givenNightShiftRoster();

        // 00:30 on 6 Sep is still the shift that started 22:00 on 5 Sep.
        clock.setLocalTime(JAKARTA, NEXT_MORNING, 0, 30);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.status").value("LATE"))
                .andExpect(jsonPath("$.data.lateMinutes").value(150));
    }

    @Test
    @DisplayName("pulang sebelum shift berakhir dihitung pulang cepat meski beda hari kalender")
    void earlyLeaveIsMeasuredAgainstNextMorningEnd() throws Exception {
        givenNightShiftRoster();

        clock.setLocalTime(JAKARTA, NIGHT, 22, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY));

        clock.setLocalTime(JAKARTA, NEXT_MORNING, 5, 30);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earlyLeaveMinutes").value(90))
                .andExpect(jsonPath("$.data.workMinutes").value(450));
    }

    @Test
    @DisplayName("check-in malam berikutnya membuat baris baru, bukan menabrak yang kemarin")
    void consecutiveNightsAreSeparateRecords() throws Exception {
        givenNightShiftRoster();

        clock.setLocalTime(JAKARTA, NIGHT, 22, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated());

        clock.setLocalTime(JAKARTA, NEXT_MORNING, 7, 0);
        mockMvc.perform(post("/api/v1/attendance/check-out")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isOk());

        clock.setLocalTime(JAKARTA, NEXT_MORNING, 22, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-06"));

        assertThat(attendanceRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("check-in kedua pada shift malam yang sama ditolak walau sudah lewat tengah malam")
    void doubleCheckInAcrossMidnightIsRejected() throws Exception {
        givenNightShiftRoster();

        clock.setLocalTime(JAKARTA, NIGHT, 22, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated());

        clock.setLocalTime(JAKARTA, NEXT_MORNING, 1, 0);
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_CHECKED_IN"));

        assertThat(attendanceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("timezone perusahaan menentukan tanggal absensi, bukan timezone server")
    void companyTimezoneDeterminesTheBusinessDate() throws Exception {
        // Jayapura is UTC+9. 00:30 local on 6 Sep is 15:30 UTC on 5 Sep; a
        // server reading UTC would file this under the wrong day.
        Company papua = companyRepository.save(com.attendance.saas.entity.Company.builder()
                .name("PT Papua")
                .code("papua")
                .email("info@papua.test")
                .timezone("Asia/Jayapura")
                .status(CompanyStatus.ACTIVE)
                .build());
        Employee worker = givenEmployee(papua, "EMP-001", "Yosef");
        Shift night = givenShift(papua, "Malam", LocalTime.of(22, 0), LocalTime.of(7, 0), 15, 10);
        givenRoster(papua, worker, night, NIGHT);
        var account = givenUser(papua, "yosef@papua.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(worker);
        userRepository.save(account);
        String papuaToken = bearer(loginAndGetAccessToken("yosef@papua.test"));

        clock.setLocalTime(ZoneId.of("Asia/Jayapura"), NEXT_MORNING, 0, 30);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", papuaToken)
                        .contentType(MediaType.APPLICATION_JSON).content(EMPTY_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jayapura"))
                .andExpect(jsonPath("$.data.checkInLocal").value("00:30:00"))
                .andExpect(jsonPath("$.data.lateMinutes").value(150));
    }
}
