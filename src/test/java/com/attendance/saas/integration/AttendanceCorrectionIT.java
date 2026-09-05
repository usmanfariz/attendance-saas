package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.AttendanceCorrection;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AttendanceStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Attendance correction")
class AttendanceCorrectionIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);
    private static final LocalDate MISSED_DAY = LocalDate.of(2026, 9, 5);

    private Company alpha;
    private Employee siti;
    private Shift dayShift;
    private String employeeToken;
    private String hrToken;

    private void givenMissedDay() throws Exception {
        alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        siti = givenEmployee(alpha, "EMP-001", "Siti");
        dayShift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, siti, dayShift, MISSED_DAY);

        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        clock.setLocalTime(JAKARTA, TODAY, 9, 0);
        employeeToken = bearer(loginAndGetAccessToken("siti@alpha.test"));
        hrToken = bearer(loginAndGetAccessToken("hr@alpha.test"));
    }

    private long createCorrection(String payload) throws Exception {
        String body = mockMvc.perform(post("/api/v1/attendance-corrections")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("koreksi disetujui membuat data absensi baru untuk hari yang terlewat")
    void approvalCreatesAttendanceForAForgottenDay() throws Exception {
        givenMissedDay();
        assertThat(attendanceRepository.count()).isZero();

        long correctionId = createCorrection("""
                {
                  "attendanceDate": "2026-09-05",
                  "checkIn": "08:00",
                  "checkOut": "17:00",
                  "reason": "Lupa melakukan check-in"
                }
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                        .header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Terverifikasi dengan rekaman CCTV"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.attendanceId").isNumber());

        Attendance created = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(alpha.getId(), siti.getId(), MISSED_DAY)
                .orElseThrow();

        assertThat(created.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(created.getLateMinutes()).isZero();
        assertThat(created.getEarlyLeaveMinutes()).isZero();
        assertThat(created.getWorkMinutes()).isEqualTo(540);
        assertThat(created.getCheckIn().atZone(JAKARTA).toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(created.getShift().getId()).isEqualTo(dayShift.getId());
    }

    @Test
    @DisplayName("koreksi menghitung ulang keterlambatan dari waktu yang dikoreksi")
    void approvalRecomputesLateness() throws Exception {
        givenMissedDay();

        long correctionId = createCorrection("""
                {
                  "attendanceDate": "2026-09-05",
                  "checkIn": "08:45",
                  "checkOut": "16:00",
                  "reason": "Lupa check-in, datang terlambat karena banjir"
                }
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                .header("Authorization", hrToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        Attendance corrected = attendanceRepository.findAll().get(0);
        assertThat(corrected.getStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(corrected.getLateMinutes()).isEqualTo(45);
        assertThat(corrected.getEarlyLeaveMinutes()).isEqualTo(60);
        assertThat(corrected.getWorkMinutes()).isEqualTo(435);
    }

    @Test
    @DisplayName("melengkapi check-out yang terlewat menyimpan nilai lama sebagai jejak audit")
    void completingACheckOutKeepsTheAuditTrail() throws Exception {
        givenMissedDay();

        // Employee checked in but forgot to check out.
        attendanceRepository.save(Attendance.builder()
                .company(alpha)
                .employee(siti)
                .shift(dayShift)
                .attendanceDate(MISSED_DAY)
                .checkIn(MISSED_DAY.atTime(8, 5).atZone(JAKARTA).toInstant())
                .status(AttendanceStatus.PRESENT)
                .build());

        long correctionId = createCorrection("""
                {
                  "attendanceDate": "2026-09-05",
                  "checkOut": "17:10",
                  "reason": "Lupa melakukan check-out"
                }
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                .header("Authorization", hrToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        Attendance corrected = attendanceRepository.findAll().get(0);
        assertThat(corrected.getCheckOut().atZone(JAKARTA).toLocalTime()).isEqualTo(LocalTime.of(17, 10));
        // The original check-in is preserved, not overwritten.
        assertThat(corrected.getCheckIn().atZone(JAKARTA).toLocalTime()).isEqualTo(LocalTime.of(8, 5));
        assertThat(corrected.getWorkMinutes()).isEqualTo(545);

        AttendanceCorrection audited = correctionRepository.findAll().get(0);
        assertThat(audited.getPreviousCheckIn().atZone(JAKARTA).toLocalTime())
                .isEqualTo(LocalTime.of(8, 5));
        assertThat(audited.getPreviousCheckOut()).isNull();
        assertThat(audited.getReviewedBy()).isNotNull();
        assertThat(audited.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("koreksi shift malam menempatkan check-out pada hari berikutnya")
    void nightShiftCorrectionRollsOverToTheNextDay() throws Exception {
        alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        siti = givenEmployee(alpha, "EMP-001", "Joko");
        Shift night = givenShift(alpha, "Malam", LocalTime.of(22, 0), LocalTime.of(7, 0), 15, 10);
        givenRoster(alpha, siti, night, MISSED_DAY);

        var account = givenUser(alpha, "joko@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        clock.setLocalTime(JAKARTA, TODAY, 9, 0);
        employeeToken = bearer(loginAndGetAccessToken("joko@alpha.test"));
        hrToken = bearer(loginAndGetAccessToken("hr@alpha.test"));

        long correctionId = createCorrection("""
                {
                  "attendanceDate": "2026-09-05",
                  "checkIn": "22:00",
                  "checkOut": "07:00",
                  "reason": "Lupa absen shift malam"
                }
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                .header("Authorization", hrToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        Attendance corrected = attendanceRepository.findAll().get(0);
        assertThat(corrected.getAttendanceDate()).isEqualTo(MISSED_DAY);
        assertThat(corrected.getCheckIn().atZone(JAKARTA).toLocalDate()).isEqualTo(MISSED_DAY);
        // 07:00 is earlier in the day than 22:00, so it belongs to the next date.
        assertThat(corrected.getCheckOut().atZone(JAKARTA).toLocalDate())
                .isEqualTo(MISSED_DAY.plusDays(1));
        assertThat(corrected.getWorkMinutes()).isEqualTo(540);
        assertThat(corrected.getEarlyLeaveMinutes()).isZero();
        assertThat(corrected.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("koreksi yang ditolak tidak mengubah data absensi")
    void rejectionLeavesAttendanceUntouched() throws Exception {
        givenMissedDay();
        long correctionId = createCorrection("""
                {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/reject", correctionId)
                        .header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Tidak ada bukti pendukung"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(attendanceRepository.count()).isZero();
    }

    @Test
    @DisplayName("koreksi tanpa check-in maupun check-out ditolak")
    void emptyCorrectionIsRejected() throws Exception {
        givenMissedDay();

        mockMvc.perform(post("/api/v1/attendance-corrections")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendanceDate":"2026-09-05","reason":"Lupa semuanya"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CORRECTION"));
    }

    @Test
    @DisplayName("koreksi untuk tanggal yang belum terjadi ditolak")
    void futureDateIsRejected() throws Exception {
        givenMissedDay();

        mockMvc.perform(post("/api/v1/attendance-corrections")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendanceDate":"2026-12-31","checkIn":"08:00","reason":"Cuma coba"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CORRECTION"));
    }

    @Test
    @DisplayName("hanya satu koreksi PENDING per tanggal")
    void onlyOnePendingCorrectionPerDate() throws Exception {
        givenMissedDay();
        createCorrection("""
                {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                """);

        mockMvc.perform(post("/api/v1/attendance-corrections")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendanceDate":"2026-09-05","checkIn":"09:00","reason":"Revisi"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CORRECTION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("koreksi yang sudah diputuskan tidak dapat diputuskan ulang")
    void aDecidedCorrectionCannotBeReviewedAgain() throws Exception {
        givenMissedDay();
        long correctionId = createCorrection("""
                {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                .header("Authorization", hrToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                        .header("Authorization", hrToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CORRECTION_NOT_PENDING"));
    }

    @Test
    @DisplayName("EMPLOYEE tidak dapat menyetujui koreksi")
    void employeeCannotApproveCorrections() throws Exception {
        givenMissedDay();
        long correctionId = createCorrection("""
                {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                """);

        mockMvc.perform(put("/api/v1/attendance-corrections/{id}/approve", correctionId)
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/attendance-corrections/me hanya menampilkan pengajuan sendiri")
    void ownCorrectionsAreScoped() throws Exception {
        givenMissedDay();
        createCorrection("""
                {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                """);

        mockMvc.perform(get("/api/v1/attendance-corrections/me")
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("EMP-001"));

        mockMvc.perform(get("/api/v1/attendance-corrections?status=PENDING")
                        .header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("koreksi tenant lain tidak dapat dibuka")
    void correctionsAreTenantScoped() throws Exception {
        givenMissedDay();
        long correctionId = createCorrection("""
                {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                """);

        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(beta, "hr@beta.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/attendance-corrections/{id}", correctionId)
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@beta.test"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CORRECTION_NOT_FOUND"));
    }
}
