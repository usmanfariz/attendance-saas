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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Attendance query API")
class AttendanceQueryIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 5);

    private Attendance givenAttendance(Company company,
                                       Employee employee,
                                       Shift shift,
                                       LocalDate date,
                                       AttendanceStatus status,
                                       int lateMinutes) {
        return attendanceRepository.save(Attendance.builder()
                .company(company)
                .employee(employee)
                .shift(shift)
                .attendanceDate(date)
                .checkIn(date.atTime(8, 0).atZone(JAKARTA).toInstant())
                .checkOut(date.atTime(17, 0).atZone(JAKARTA).toInstant())
                .status(status)
                .lateMinutes(lateMinutes)
                .workMinutes(540)
                .build());
    }

    @Test
    @DisplayName("HR dapat memfilter absensi berdasarkan tanggal, status, dan departemen")
    void hrCanFilterAttendance() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Department production = givenDepartment(alpha, "Produksi");
        Department finance = givenDepartment(alpha, "Keuangan");
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);

        Employee siti = givenEmployee(alpha, "EMP-001", "Siti", production, null, EmployeeStatus.ACTIVE);
        Employee budi = givenEmployee(alpha, "EMP-002", "Budi", finance, null, EmployeeStatus.ACTIVE);

        givenAttendance(alpha, siti, shift, DAY, AttendanceStatus.PRESENT, 0);
        givenAttendance(alpha, siti, shift, DAY.minusDays(1), AttendanceStatus.LATE, 25);
        givenAttendance(alpha, budi, shift, DAY, AttendanceStatus.LATE, 30);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        mockMvc.perform(get("/api/v1/attendance").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3));

        mockMvc.perform(get("/api/v1/attendance?status=LATE").header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/attendance?startDate=2026-09-05&endDate=2026-09-05")
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/attendance?departmentId=" + production.getId())
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/v1/attendance?employeeId=" + budi.getId())
                        .header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("EMP-002"));
    }

    @Test
    @DisplayName("rentang tanggal terbalik ditolak")
    void reversedRangeRejected() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/attendance?startDate=2026-09-10&endDate=2026-09-01")
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("/attendance/me hanya menampilkan absensi milik sendiri")
    void ownHistoryIsScopedToTheCaller() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        Employee budi = givenEmployee(alpha, "EMP-002", "Budi");

        givenAttendance(alpha, siti, shift, DAY, AttendanceStatus.PRESENT, 0);
        givenAttendance(alpha, siti, shift, DAY.minusDays(1), AttendanceStatus.PRESENT, 0);
        givenAttendance(alpha, budi, shift, DAY, AttendanceStatus.PRESENT, 0);

        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);

        mockMvc.perform(get("/api/v1/attendance/me")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.data[0].employeeCode").value("EMP-001"));
    }

    @Test
    @DisplayName("EMPLOYEE tidak boleh melihat absensi seluruh karyawan")
    void employeeCannotListEveryone() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(siti);
        userRepository.save(account);

        mockMvc.perform(get("/api/v1/attendance")
                        .header("Authorization", bearer(loginAndGetAccessToken("siti@alpha.test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("absensi tenant lain tidak terlihat dan tidak dapat dibuka lewat id")
    void attendanceOfAnotherTenantIsInvisible() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        Shift betaShift = givenShift(beta, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        Employee betaEmployee = givenEmployee(beta, "EMP-001", "Karyawan Beta");
        Attendance betaAttendance =
                givenAttendance(beta, betaEmployee, betaShift, DAY, AttendanceStatus.PRESENT, 0);

        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("hr@alpha.test"));

        mockMvc.perform(get("/api/v1/attendance").header("Authorization", token))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/attendance/{id}", betaAttendance.getId())
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ATTENDANCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("waktu absensi juga disajikan dalam jam lokal perusahaan")
    void responseCarriesCompanyLocalTime() throws Exception {
        Company alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Shift shift = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        Employee siti = givenEmployee(alpha, "EMP-001", "Siti");
        Attendance attendance = givenAttendance(alpha, siti, shift, DAY, AttendanceStatus.PRESENT, 0);
        givenUser(alpha, "hr@alpha.test", UserRole.HR, UserStatus.ACTIVE);

        mockMvc.perform(get("/api/v1/attendance/{id}", attendance.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("hr@alpha.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInLocal").value("08:00:00"))
                .andExpect(jsonPath("$.data.checkOutLocal").value("17:00:00"))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Jakarta"))
                .andExpect(jsonPath("$.data.shiftStartTime").value("08:00"));
    }
}
