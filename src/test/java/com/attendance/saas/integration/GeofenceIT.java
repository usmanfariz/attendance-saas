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
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Geofencing")
class GeofenceIT extends AbstractIntegrationTest {

    private static final ZoneId JAKARTA = ZoneId.of("Asia/Jakarta");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 7);

    /** Monas, Jakarta — the office in these tests. */
    private static final String OFFICE_LAT = "-6.1753924";
    private static final String OFFICE_LON = "106.8271528";

    private Company alpha;
    private String employeeToken;

    private void givenEmployeeReadyToCheckIn() throws Exception {
        alpha = givenCompany("alpha", CompanyStatus.ACTIVE);
        Employee employee = givenEmployee(alpha, "EMP-001", "Siti");
        Shift day = givenShift(alpha, "Pagi", LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 10);
        givenRoster(alpha, employee, day, DAY);

        var account = givenUser(alpha, "siti@alpha.test", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(employee);
        userRepository.save(account);
        employeeToken = bearer(loginAndGetAccessToken("siti@alpha.test"));
        clock.setLocalTime(JAKARTA, DAY, 8, 0);
    }

    private String checkInAt(String latitude, String longitude) {
        return """
                {"latitude":%s,"longitude":%s}
                """.formatted(latitude, longitude);
    }

    @Test
    @DisplayName("dengan geofence nonaktif, check-in dari mana pun diterima")
    void geofenceOffAcceptsAnyLocation() throws Exception {
        givenEmployeeReadyToCheckIn();
        givenLocation(alpha, "Kantor Pusat", OFFICE_LAT, OFFICE_LON, 100);
        givenGeofenceEnabled(alpha, false);

        // Bandung, roughly 120 km away.
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt("-6.914744", "107.609810")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("di dalam radius diterima")
    void insideRadiusIsAccepted() throws Exception {
        givenEmployeeReadyToCheckIn();
        givenLocation(alpha, "Kantor Pusat", OFFICE_LAT, OFFICE_LON, 150);
        givenGeofenceEnabled(alpha, true);

        // About 60 m north of the office.
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt("-6.1748524", "106.8271528")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PRESENT"));
    }

    @Test
    @DisplayName("di luar radius ditolak dan tidak menyisakan data absensi")
    void outsideRadiusIsRejected() throws Exception {
        givenEmployeeReadyToCheckIn();
        givenLocation(alpha, "Kantor Pusat", OFFICE_LAT, OFFICE_LON, 100);
        givenGeofenceEnabled(alpha, true);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt("-6.914744", "107.609810")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("OUTSIDE_GEOFENCE"));

        // A refused check-in must leave nothing behind.
        assertThat(attendanceRepository.count()).isZero();
    }

    @Test
    @DisplayName("koordinat wajib dikirim ketika geofence aktif")
    void coordinatesAreRequiredWhenEnabled() throws Exception {
        givenEmployeeReadyToCheckIn();
        givenLocation(alpha, "Kantor Pusat", OFFICE_LAT, OFFICE_LON, 100);
        givenGeofenceEnabled(alpha, true);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("LOCATION_REQUIRED"));
    }

    @Test
    @DisplayName("geofence aktif tanpa lokasi kantor memberi error yang jelas")
    void enabledWithoutAnyOfficeIsReported() throws Exception {
        givenEmployeeReadyToCheckIn();
        givenGeofenceEnabled(alpha, true);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt("-6.1753924", "106.8271528")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_LOCATION"));
    }

    @Test
    @DisplayName("cukup berada dalam radius salah satu kantor")
    void anyOfficeWithinRadiusIsEnough() throws Exception {
        givenEmployeeReadyToCheckIn();
        givenLocation(alpha, "Kantor Pusat", OFFICE_LAT, OFFICE_LON, 100);
        givenLocation(alpha, "Cabang Bandung", "-6.914744", "107.609810", 200);
        givenGeofenceEnabled(alpha, true);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt("-6.914744", "107.609810")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("lokasi yang dinonaktifkan tidak lagi mengizinkan check-in")
    void inactiveLocationDoesNotCount() throws Exception {
        givenEmployeeReadyToCheckIn();
        var office = givenLocation(alpha, "Kantor Lama", OFFICE_LAT, OFFICE_LON, 100);
        office.setActive(false);
        locationRepository.save(office);
        givenGeofenceEnabled(alpha, true);

        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt(OFFICE_LAT, OFFICE_LON)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_LOCATION"));
    }

    @Test
    @DisplayName("COMPANY_ADMIN mengelola lokasi dan menyalakan geofence lewat API")
    void adminManagesLocationsAndSettings() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        String token = bearer(loginAndGetAccessToken("admin@alpha.test"));

        mockMvc.perform(post("/api/v1/locations")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Kantor Pusat Jakarta",
                                  "address": "Jl. Merdeka 1",
                                  "latitude": -6.1753924,
                                  "longitude": 106.8271528,
                                  "radiusMeter": 150
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.radiusMeter").value(150))
                .andExpect(jsonPath("$.data.active").value(true));

        mockMvc.perform(get("/api/v1/locations/settings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.geofenceEnabled").value(false))
                .andExpect(jsonPath("$.data.activeLocationCount").value(1));

        mockMvc.perform(put("/api/v1/locations/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"geofenceEnabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.geofenceEnabled").value(true));
    }

    @Test
    @DisplayName("radius di bawah batas minimum ditolak")
    void radiusBelowMinimumIsRejected() throws Exception {
        Company company = givenCompany("alpha", CompanyStatus.ACTIVE);
        givenUser(company, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/api/v1/locations")
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Terlalu Sempit","latitude":-6.17,"longitude":106.82,"radiusMeter":1}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("lokasi tenant lain tidak terlihat dan tidak memengaruhi geofence")
    void locationsAreTenantScoped() throws Exception {
        givenEmployeeReadyToCheckIn();
        Company beta = givenCompany("beta", CompanyStatus.ACTIVE);
        var betaOffice = givenLocation(beta, "Kantor Beta", OFFICE_LAT, OFFICE_LON, 500);
        givenGeofenceEnabled(alpha, true);

        // Alpha has no office of its own, so Beta's must not stand in for it.
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkInAt(OFFICE_LAT, OFFICE_LON)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("NO_ACTIVE_LOCATION"));

        givenUser(alpha, "admin@alpha.test", UserRole.COMPANY_ADMIN, UserStatus.ACTIVE);
        mockMvc.perform(get("/api/v1/locations/{id}", betaOffice.getId())
                        .header("Authorization", bearer(loginAndGetAccessToken("admin@alpha.test"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("LOCATION_NOT_FOUND"));
    }
}
