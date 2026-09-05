package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Attendance reports")
class ReportIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    private Company alpha;
    private Shift shift;
    private Department production;
    private Department finance;
    private Employee siti;
    private Employee budi;
    private String hrToken;

    private void givenAMonthOfAttendance() throws Exception {
        alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        production = givenDepartment(alpha, "Produksi");
        finance = givenDepartment(alpha, "Keuangan");

        siti = givenEmployee(alpha, "EMP-001", "Siti", production, null, EmployeeStatus.ACTIVE);
        budi = givenEmployee(alpha, "EMP-002", "Budi", finance, null, EmployeeStatus.ACTIVE);

        givenRoster(alpha, siti, shift, DAY);
        givenRoster(alpha, budi, shift, DAY);

        // Siti: present, late, and a leave day.
        record(siti, DAY, AttendanceStatus.PRESENT, 0, 0, 540);
        record(siti, DAY.minusDays(1), AttendanceStatus.LATE, 25, 0, 515);
        record(siti, DAY.minusDays(2), AttendanceStatus.LEAVE, 0, 0, 0);
        // Budi: one late day with an early leave.
        record(budi, DAY, AttendanceStatus.LATE, 10, 45, 485);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        clock.setLocalTime(JAKARTA, DAY, 18, 0);
        hrToken = bearer(loginAndGetAccessToken("hr@alpha.test"));
    }

    private Attendance record(Employee employee,
                              LocalDate date,
                              AttendanceStatus status,
                              int lateMinutes,
                              int earlyLeaveMinutes,
                              int workMinutes) {
        return attendanceRepository.save(Attendance.builder()
                .company(alpha)
                .employee(employee)
                .shift(shift)
                .attendanceDate(date)
                .checkIn(status.isWorked() ? date.atTime(8, 0).atZone(JAKARTA).toInstant() : null)
                .checkOut(status.isWorked() ? date.atTime(17, 0).atZone(JAKARTA).toInstant() : null)
                .status(status)
                .lateMinutes(lateMinutes)
                .earlyLeaveMinutes(earlyLeaveMinutes)
                .workMinutes(workMinutes)
                .build());
    }

    @Test
    @DisplayName("laporan harian menampilkan baris per karyawan beserta ringkasannya")
    void dailyReport() throws Exception {
        givenAMonthOfAttendance();

        mockMvc.perform(get("/api/v1/reports/attendance/daily?date=2026-09-07")
                        .header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-09-07"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"))
                .andExpect(jsonPath("$.data.rows.length()").value(2))
                .andExpect(jsonPath("$.data.summary.totalEmployees").value(2))
                .andExpect(jsonPath("$.data.summary.expected").value(2))
                // On 7 Sep: Siti is PRESENT, Budi is LATE.
                .andExpect(jsonPath("$.data.summary.present").value(1))
                .andExpect(jsonPath("$.data.summary.late").value(1))
                .andExpect(jsonPath("$.data.summary.absent").value(0))
                .andExpect(jsonPath("$.data.rows[0].employeeName").value("Budi"))
                .andExpect(jsonPath("$.data.rows[0].departmentName").value("Keuangan"))
                .andExpect(jsonPath("$.data.rows[0].checkIn").value("08:00:00"));
    }

    @Test
    @DisplayName("laporan harian menghitung karyawan terjadwal yang tidak punya catatan sebagai absen")
    void dailyReportCountsAbsentees() throws Exception {
        givenAMonthOfAttendance();
        Employee mangkir = givenEmployee(alpha, "EMP-003", "Mangkir", production, null, EmployeeStatus.ACTIVE);
        givenRoster(alpha, mangkir, shift, DAY);

        mockMvc.perform(get("/api/v1/reports/attendance/daily?date=2026-09-07")
                        .header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.expected").value(3))
                .andExpect(jsonPath("$.data.summary.absent").value(1))
                .andExpect(jsonPath("$.data.rows.length()").value(2));
    }

    @Test
    @DisplayName("laporan harian dapat difilter per departemen dan status")
    void dailyReportFilters() throws Exception {
        givenAMonthOfAttendance();

        mockMvc.perform(get("/api/v1/reports/attendance/daily?date=2026-09-07&departmentId="
                        + production.getId()).header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].employeeCode").value("EMP-001"));

        mockMvc.perform(get("/api/v1/reports/attendance/daily?date=2026-09-06&status=LATE")
                        .header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(1))
                .andExpect(jsonPath("$.data.rows[0].lateMinutes").value(25));
    }

    @Test
    @DisplayName("laporan bulanan merekap per karyawan")
    void monthlyReport() throws Exception {
        givenAMonthOfAttendance();

        mockMvc.perform(get("/api/v1/reports/attendance/monthly?year=2026&month=9")
                        .header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2026))
                .andExpect(jsonPath("$.data.month").value(9))
                .andExpect(jsonPath("$.data.startDate").value("2026-09-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-09-30"))
                .andExpect(jsonPath("$.data.employeeCount").value(2))
                // Sorted by employee name: Budi first.
                .andExpect(jsonPath("$.data.rows[0].employeeName").value("Budi"))
                .andExpect(jsonPath("$.data.rows[0].recordedDays").value(1))
                .andExpect(jsonPath("$.data.rows[0].lateDays").value(1))
                .andExpect(jsonPath("$.data.rows[0].totalEarlyLeaveMinutes").value(45))
                .andExpect(jsonPath("$.data.rows[1].employeeName").value("Siti"))
                .andExpect(jsonPath("$.data.rows[1].recordedDays").value(3))
                .andExpect(jsonPath("$.data.rows[1].presentDays").value(1))
                .andExpect(jsonPath("$.data.rows[1].lateDays").value(1))
                .andExpect(jsonPath("$.data.rows[1].leaveDays").value(1))
                .andExpect(jsonPath("$.data.rows[1].totalLateMinutes").value(25))
                .andExpect(jsonPath("$.data.rows[1].totalWorkMinutes").value(1055));
    }

    @Test
    @DisplayName("laporan bulanan dapat difilter per departemen")
    void monthlyReportFilteredByDepartment() throws Exception {
        givenAMonthOfAttendance();

        mockMvc.perform(get("/api/v1/reports/attendance/monthly?year=2026&month=9&departmentId="
                        + finance.getId()).header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCount").value(1))
                .andExpect(jsonPath("$.data.rows[0].employeeCode").value("EMP-002"));
    }

    @Test
    @DisplayName("laporan per karyawan berisi ringkasan dan seluruh catatannya")
    void employeeReport() throws Exception {
        givenAMonthOfAttendance();

        mockMvc.perform(get("/api/v1/reports/attendance/employee/{id}?startDate=2026-09-01&endDate=2026-09-30",
                        siti.getId()).header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employeeCode").value("EMP-001"))
                .andExpect(jsonPath("$.data.departmentName").value("Produksi"))
                .andExpect(jsonPath("$.data.summary.recordedDays").value(3))
                .andExpect(jsonPath("$.data.summary.totalWorkMinutes").value(1055))
                .andExpect(jsonPath("$.data.records.length()").value(3))
                // Ordered by date ascending.
                .andExpect(jsonPath("$.data.records[0].attendanceDate").value("2026-09-05"))
                .andExpect(jsonPath("$.data.records[2].attendanceDate").value("2026-09-07"))
                .andExpect(jsonPath("$.data.records[2].checkInLocal").value("08:00:00"));
    }

    @Test
    @DisplayName("laporan karyawan tanpa data mengembalikan ringkasan nol, bukan error")
    void employeeReportWithNoData() throws Exception {
        givenAMonthOfAttendance();
        Employee baru = givenEmployee(alpha, "EMP-009", "Karyawan Baru");

        mockMvc.perform(get("/api/v1/reports/attendance/employee/{id}", baru.getId())
                        .header("Authorization", hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.recordedDays").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    @DisplayName("rentang tanggal terbalik ditolak")
    void reversedRangeIsRejected() throws Exception {
        givenAMonthOfAttendance();

        mockMvc.perform(get("/api/v1/reports/attendance/employee/{id}?startDate=2026-09-30&endDate=2026-09-01",
                        siti.getId()).header("Authorization", hrToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("karyawan tenant lain tidak dapat dilaporkan")
    void employeeReportIsTenantScoped() throws Exception {
        givenAMonthOfAttendance();
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Employee betaEmployee = givenEmployee(beta, "EMP-001", "Karyawan Beta");

        mockMvc.perform(get("/api/v1/reports/attendance/employee/{id}", betaEmployee.getId())
                        .header("Authorization", hrToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("EMPLOYEE_NOT_FOUND"));
    }

    @Test
    @DisplayName("EMPLOYEE tidak boleh mengakses laporan")
    void employeeRoleCannotAccessReports() throws Exception {
        givenAMonthOfAttendance();
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);

        mockMvc.perform(get("/api/v1/reports/attendance/daily")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isForbidden());
    }
}
