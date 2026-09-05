package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AttendanceStatus;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Dashboard")
class DashboardIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    private Attendance givenAttendance(Company company,
                                       Employee employee,
                                       Shift shift,
                                       LocalDate date,
                                       AttendanceStatus status,
                                       int lateMinutes,
                                       int workMinutes) {
        return attendanceRepository.save(Attendance.builder()
                .company(company)
                .employee(employee)
                .shift(shift)
                .attendanceDate(date)
                .checkIn(status.isWorked() ? date.atTime(8, 0).atZone(JAKARTA).toInstant() : null)
                .status(status)
                .lateMinutes(lateMinutes)
                .workMinutes(workMinutes)
                .build());
    }

    @Test
    @DisplayName("dashboard perusahaan menghitung hadir, terlambat, cuti, dan absen")
    void companyDashboardCounts() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);

        Employee hadir = givenEmployee(alpha, "EMP-001", "Hadir");
        Employee telat = givenEmployee(alpha, "EMP-002", "Telat");
        Employee cuti = givenEmployee(alpha, "EMP-003", "Cuti");
        Employee mangkir = givenEmployee(alpha, "EMP-004", "Mangkir");

        // All four are rostered today; only three have a record.
        for (Employee employee : new Employee[]{hadir, telat, cuti, mangkir}) {
            givenRoster(alpha, employee, shift, DAY);
        }
        givenAttendance(alpha, hadir, shift, DAY, AttendanceStatus.PRESENT, 0, 540);
        givenAttendance(alpha, telat, shift, DAY, AttendanceStatus.LATE, 30, 510);
        givenAttendance(alpha, cuti, shift, DAY, AttendanceStatus.LEAVE, 0, 0);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        clock.setLocalTime(JAKARTA, DAY, 10, 0);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-09-07"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"))
                .andExpect(jsonPath("$.data.totalEmployees").value(4))
                .andExpect(jsonPath("$.data.expectedToday").value(4))
                .andExpect(jsonPath("$.data.present").value(1))
                .andExpect(jsonPath("$.data.late").value(1))
                .andExpect(jsonPath("$.data.leave").value(1))
                .andExpect(jsonPath("$.data.absent").value(1));
    }

    @Test
    @DisplayName("karyawan tanpa jadwal tidak dihitung sebagai absen")
    void employeesWithoutARosterAreNotAbsent() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);

        Employee bekerja = givenEmployee(alpha, "EMP-001", "Bekerja");
        givenEmployee(alpha, "EMP-002", "Libur");
        givenEmployee(alpha, "EMP-003", "Libur juga");

        givenRoster(alpha, bekerja, shift, DAY);
        givenAttendance(alpha, bekerja, shift, DAY, AttendanceStatus.PRESENT, 0, 540);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        clock.setLocalTime(JAKARTA, DAY, 10, 0);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(3))
                .andExpect(jsonPath("$.data.expectedToday").value(1))
                .andExpect(jsonPath("$.data.absent").value(0));
    }

    @Test
    @DisplayName("dengan shift default, seluruh karyawan aktif dianggap terjadwal")
    void defaultShiftMakesEveryoneExpected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Shift shift = givenShift(alpha, "Reguler", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        shift.setDefaultShift(true);
        shiftRepository.save(shift);

        Employee hadir = givenEmployee(alpha, "EMP-001", "Hadir");
        givenEmployee(alpha, "EMP-002", "Mangkir");
        givenEmployee(alpha, "EMP-003", "Resign", null, null, EmployeeStatus.RESIGNED);
        givenAttendance(alpha, hadir, shift, DAY, AttendanceStatus.PRESENT, 0, 540);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        clock.setLocalTime(JAKARTA, DAY, 10, 0);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                // The resigned employee counts neither in the headcount nor as absent.
                .andExpect(jsonPath("$.data.totalEmployees").value(2))
                .andExpect(jsonPath("$.data.expectedToday").value(2))
                .andExpect(jsonPath("$.data.absent").value(1));
    }

    @Test
    @DisplayName("dashboard menampilkan jumlah pengajuan yang menunggu persetujuan")
    void pendingRequestsAreSurfaced() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        clock.setLocalTime(JAKARTA, DAY, 10, 0);
        String employeeToken = bearer(loginAndGetAccessToken("siti@alpha.test"));

        mockMvc.perform(post("/api/v1/leave")
                .header("Authorization", employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"leaveType":"CUTI","startDate":"2026-09-20","endDate":"2026-09-21","reason":"Keluarga"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/attendance-corrections")
                .header("Authorization", employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"attendanceDate":"2026-09-05","checkIn":"08:00","reason":"Lupa check-in"}
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingLeaveRequests").value(1))
                .andExpect(jsonPath("$.data.pendingCorrections").value(1));
    }

    @Test
    @DisplayName("dashboard karyawan menampilkan absensi hari ini, shift, rekap bulan, dan sisa cuti")
    void employeeDashboard() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        givenRoster(alpha, siti, shift, DAY);

        givenAttendance(alpha, siti, shift, DAY, AttendanceStatus.PRESENT, 0, 540);
        givenAttendance(alpha, siti, shift, DAY.minusDays(1), AttendanceStatus.LATE, 20, 520);
        givenAttendance(alpha, siti, shift, DAY.minusDays(2), AttendanceStatus.PRESENT, 0, 540);
        // Previous month must not leak into "this month".
        givenAttendance(alpha, siti, shift, LocalDate.of(2026, 8, 20), AttendanceStatus.LATE, 99, 400);

        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);
        clock.setLocalTime(JAKARTA, DAY, 10, 0);

        mockMvc.perform(get("/api/v1/dashboard/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.data.todayAttendance.status").value("PRESENT"))
                .andExpect(jsonPath("$.data.currentShift.name").value("Pagi"))
                .andExpect(jsonPath("$.data.thisMonth.monthStart").value("2026-09-01"))
                .andExpect(jsonPath("$.data.thisMonth.presentDays").value(2))
                .andExpect(jsonPath("$.data.thisMonth.lateDays").value(1))
                .andExpect(jsonPath("$.data.thisMonth.totalLateMinutes").value(20))
                .andExpect(jsonPath("$.data.thisMonth.totalWorkMinutes").value(1600))
                .andExpect(jsonPath("$.data.leaveBalance.quotaDays").value(12))
                .andExpect(jsonPath("$.data.leaveBalance.usedDays").value(0))
                .andExpect(jsonPath("$.data.leaveBalance.remainingDays").value(12));
    }

    @Test
    @DisplayName("cuti yang disetujui mengurangi sisa kuota tahunan")
    void approvedLeaveReducesTheBalance() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);
        givenUser(alpha, "spv@alpha.test", UserRole.SUPERVISOR, UserStatus.ACTIVE);

        clock.setLocalTime(JAKARTA, DAY, 10, 0);
        String employeeToken = bearer(loginAndGetAccessToken("siti@alpha.test"));
        String supervisorToken = bearer(loginAndGetAccessToken("spv@alpha.test"));

        String body = mockMvc.perform(post("/api/v1/leave")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaveType":"CUTI","startDate":"2026-09-20","endDate":"2026-09-22","reason":"Keluarga"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long leaveId = json(body).path("data").path("id").asLong();

        // Still pending: quota untouched, but visible as pendingDays.
        mockMvc.perform(get("/api/v1/dashboard/me").header("Authorization", employeeToken))
                .andExpect(jsonPath("$.data.leaveBalance.usedDays").value(0))
                .andExpect(jsonPath("$.data.leaveBalance.pendingDays").value(3))
                .andExpect(jsonPath("$.data.leaveBalance.remainingDays").value(12));

        mockMvc.perform(put("/api/v1/leave/{id}/approve", leaveId)
                .header("Authorization", supervisorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/me").header("Authorization", employeeToken))
                .andExpect(jsonPath("$.data.leaveBalance.usedDays").value(3))
                .andExpect(jsonPath("$.data.leaveBalance.remainingDays").value(9))
                .andExpect(jsonPath("$.data.leaveBalance.pendingDays").value(0));
    }

    @Test
    @DisplayName("dashboard hanya memuat data tenant sendiri")
    void dashboardIsTenantScoped() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Shift betaShift = givenShift(beta, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        Employee betaEmployee = givenEmployee(beta, "EMP-001", "Karyawan Beta");
        givenRoster(beta, betaEmployee, betaShift, DAY);
        givenAttendance(beta, betaEmployee, betaShift, DAY, AttendanceStatus.PRESENT, 0, 540);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        clock.setLocalTime(JAKARTA, DAY, 10, 0);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(0))
                .andExpect(jsonPath("$.data.present").value(0))
                .andExpect(jsonPath("$.data.absent").value(0));
    }

    @Test
    @DisplayName("EMPLOYEE tidak boleh membuka dashboard perusahaan")
    void employeeCannotSeeCompanyDashboard() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);

        mockMvc.perform(get("/api/v1/dashboard")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isForbidden());
    }
}
