package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Leave management")
class LeaveManagementIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    private Company alpha;
    private Employee siti;
    private String employeeToken;
    private String supervisorToken;

    private void givenEmployeeAndSupervisor() throws Exception {
        alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        siti = givenEmployee(alpha, "EMP-001", "Siti");

        var employeeAccount = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        employeeAccount.setEmployee(siti);
        userRepository.save(employeeAccount);

        givenUser(alpha, "spv@alpha.test", UserRole.SUPERVISOR, UserStatus.ACTIVE);

        clock.setLocalTime(JAKARTA, TODAY, 9, 0);
        employeeToken = bearer(loginAndGetAccessToken("siti@alpha.test"));
        supervisorToken = bearer(loginAndGetAccessToken("spv@alpha.test"));
    }

    private String leavePayload(String type, String start, String end) {
        return """
                {"leaveType":"%s","startDate":"%s","endDate":"%s","reason":"Acara keluarga"}
                """.formatted(type, start, end);
    }

    private long createLeave(String payload) throws Exception {
        String body = mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).path("data").path("id").asLong();
    }

    @Test
    @DisplayName("pengajuan cuti menghitung total hari dan berstatus PENDING")
    void creatingLeaveComputesTotalDays() throws Exception {
        givenEmployeeAndSupervisor();

        mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leavePayload("CUTI", "2026-09-10", "2026-09-12")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.totalDays").value(3))
                .andExpect(jsonPath("$.data.leaveType").value("CUTI"))
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-001"));
    }

    @Test
    @DisplayName("pengajuan yang beririsan dengan pengajuan aktif ditolak")
    void overlappingLeaveIsRejected() throws Exception {
        givenEmployeeAndSupervisor();
        createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-12"));

        // Overlaps on 12 September.
        mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leavePayload("IZIN", "2026-09-12", "2026-09-14")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LEAVE_ALREADY_EXISTS"));

        // Adjacent but not overlapping is fine.
        mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leavePayload("IZIN", "2026-09-13", "2026-09-14")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("pengajuan yang dibatalkan tidak lagi memblokir tanggal yang sama")
    void cancelledLeaveNoLongerBlocks() throws Exception {
        givenEmployeeAndSupervisor();
        long leaveId = createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-12"));

        mockMvc.perform(put("/api/v1/leave/{id}/cancel", leaveId)
                        .header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leavePayload("CUTI", "2026-09-10", "2026-09-12")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("persetujuan menandai absensi sehingga hari cuti tidak terhitung ABSENT")
    void approvalMarksAttendanceDays() throws Exception {
        givenEmployeeAndSupervisor();
        long leaveId = createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-12"));

        mockMvc.perform(put("/api/v1/leave/{id}/approve", leaveId)
                        .header("Authorization", supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Disetujui, pekerjaan sudah didelegasikan"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedByName").value("spv@alpha.test"))
                .andExpect(jsonPath("$.data.reviewNote").value("Disetujui, pekerjaan sudah didelegasikan"));

        List<Attendance> marked = attendanceRepository.findAll();
        assertThat(marked).hasSize(3);
        assertThat(marked).allSatisfy(attendance -> {
            assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.LEAVE);
            assertThat(attendance.hasCheckedIn()).isFalse();
        });
        assertThat(marked).extracting(Attendance::getAttendanceDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 11),
                        LocalDate.of(2026, 9, 12));
    }

    @Test
    @DisplayName("jenis SAKIT dan IZIN menandai absensi dengan status masing-masing")
    void leaveTypeDeterminesAttendanceStatus() throws Exception {
        givenEmployeeAndSupervisor();
        long sickId = createLeave(leavePayload("SAKIT", "2026-09-10", "2026-09-10"));

        mockMvc.perform(put("/api/v1/leave/{id}/approve", sickId)
                .header("Authorization", supervisorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertThat(attendanceRepository.findAll())
                .singleElement()
                .extracting(Attendance::getStatus)
                .isEqualTo(AttendanceStatus.SICK);
    }

    @Test
    @DisplayName("hari yang sudah ada check-in tidak ditimpa oleh persetujuan cuti")
    void anExistingCheckInSurvivesApproval() throws Exception {
        givenEmployeeAndSupervisor();
        Shift day = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        LocalDate workedDay = LocalDate.of(2026, 9, 10);

        attendanceRepository.save(Attendance.builder()
                .company(alpha)
                .employee(siti)
                .shift(day)
                .attendanceDate(workedDay)
                .checkIn(workedDay.atTime(8, 0).atZone(JAKARTA).toInstant())
                .status(AttendanceStatus.PRESENT)
                .build());

        long leaveId = createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-11"));
        mockMvc.perform(put("/api/v1/leave/{id}/approve", leaveId)
                .header("Authorization", supervisorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        Attendance worked = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(alpha.getId(), siti.getId(), workedDay)
                .orElseThrow();
        assertThat(worked.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(worked.hasCheckedIn()).isTrue();

        Attendance leaveDay = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(
                        alpha.getId(), siti.getId(), LocalDate.of(2026, 9, 11))
                .orElseThrow();
        assertThat(leaveDay.getStatus()).isEqualTo(AttendanceStatus.LEAVE);
    }

    @Test
    @DisplayName("penolakan wajib menyertakan alasan")
    void rejectionRequiresANote() throws Exception {
        givenEmployeeAndSupervisor();
        long leaveId = createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-12"));

        mockMvc.perform(put("/api/v1/leave/{id}/reject", leaveId)
                        .header("Authorization", supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/api/v1/leave/{id}/reject", leaveId)
                        .header("Authorization", supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Beban kerja tim sedang tinggi"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // Rejection must not touch attendance.
        assertThat(attendanceRepository.count()).isZero();
    }

    @Test
    @DisplayName("pengajuan yang sudah diputuskan tidak dapat diputuskan ulang")
    void aDecidedRequestCannotBeReviewedAgain() throws Exception {
        givenEmployeeAndSupervisor();
        long leaveId = createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-10"));

        mockMvc.perform(put("/api/v1/leave/{id}/approve", leaveId)
                .header("Authorization", supervisorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/leave/{id}/reject", leaveId)
                        .header("Authorization", supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Berubah pikiran"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LEAVE_NOT_PENDING"));
    }

    @Test
    @DisplayName("tidak seorang pun dapat menyetujui pengajuannya sendiri")
    void nobodyApprovesTheirOwnRequest() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee supervisorEmployee = givenEmployee(company, "EMP-100", "Pak Supervisor");
        var account = givenUser(company, "spv@alpha.test", UserRole.SUPERVISOR, UserStatus.ACTIVE);
        account.setEmployee(supervisorEmployee);
        userRepository.save(account);

        clock.setLocalTime(JAKARTA, TODAY, 9, 0);
        String token = bearer(loginAndGetAccessToken("spv@alpha.test"));

        String body = mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leavePayload("CUTI", "2026-09-10", "2026-09-10")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long leaveId = json(body).path("data").path("id").asLong();

        mockMvc.perform(put("/api/v1/leave/{id}/approve", leaveId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_REVIEW_OWN_REQUEST"));
    }

    @Test
    @DisplayName("EMPLOYEE tidak dapat menyetujui pengajuan siapa pun")
    void employeeCannotApprove() throws Exception {
        givenEmployeeAndSupervisor();
        long leaveId = createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-10"));

        mockMvc.perform(put("/api/v1/leave/{id}/approve", leaveId)
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/leave/me hanya menampilkan pengajuan sendiri")
    void ownRequestsAreScoped() throws Exception {
        givenEmployeeAndSupervisor();
        Employee budi = givenEmployee(alpha, "EMP-002", "Budi");
        var budiAccount = givenUser(alpha, "budi@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        budiAccount.setEmployee(budi);
        userRepository.save(budiAccount);

        createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-10"));

        mockMvc.perform(post("/api/v1/leave")
                .header("Authorization", bearer(loginAndGetAccessToken("budi@alpha.test")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(leavePayload("IZIN", "2026-09-15", "2026-09-15")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/leave/me").header("Authorization", employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("EMP-001"));

        mockMvc.perform(get("/api/v1/leave").header("Authorization", supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("pengajuan cuti tenant lain tidak terlihat")
    void leaveIsTenantScoped() throws Exception {
        givenEmployeeAndSupervisor();
        createLeave(leavePayload("CUTI", "2026-09-10", "2026-09-10"));

        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        givenUser(beta, "hr@beta.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/leave")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@beta.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("rentang tanggal terbalik ditolak")
    void reversedRangeIsRejected() throws Exception {
        givenEmployeeAndSupervisor();

        mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leavePayload("CUTI", "2026-09-12", "2026-09-10")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
    }
}
