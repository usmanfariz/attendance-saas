package com.attendance.saas.integration;

import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.CompanySettings;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.EmployeeShift;
import com.attendance.saas.entity.Location;
import com.attendance.saas.entity.Plan;
import com.attendance.saas.entity.Subscription;
import com.attendance.saas.entity.Position;
import com.attendance.saas.entity.Shift;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.BillingPeriod;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.SubscriptionStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.CompanySettingsRepository;
import com.attendance.saas.repository.DepartmentRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.PositionRepository;
import com.attendance.saas.repository.AttendanceCorrectionRepository;
import com.attendance.saas.repository.AuditLogRepository;
import com.attendance.saas.repository.EmployeeShiftRepository;
import com.attendance.saas.repository.LeaveRequestRepository;
import com.attendance.saas.repository.InvoiceRepository;
import com.attendance.saas.repository.LocationRepository;
import com.attendance.saas.repository.PlanRepository;
import com.attendance.saas.repository.SubscriptionRepository;
import com.attendance.saas.repository.RefreshTokenRepository;
import com.attendance.saas.repository.ShiftRepository;
import com.attendance.saas.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected CompanyRepository companyRepository;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;
    @Autowired
    protected DepartmentRepository departmentRepository;
    @Autowired
    protected PositionRepository positionRepository;
    @Autowired
    protected EmployeeRepository employeeRepository;
    @Autowired
    protected ShiftRepository shiftRepository;
    @Autowired
    protected EmployeeShiftRepository employeeShiftRepository;
    @Autowired
    protected AttendanceRepository attendanceRepository;
    @Autowired
    protected CompanySettingsRepository companySettingsRepository;
    @Autowired
    protected LocationRepository locationRepository;
    @Autowired
    protected LeaveRequestRepository leaveRequestRepository;
    @Autowired
    protected AttendanceCorrectionRepository correctionRepository;
    @Autowired
    protected AuditLogRepository auditLogRepository;
    @Autowired
    protected PlanRepository planRepository;
    @Autowired
    protected SubscriptionRepository subscriptionRepository;
    @Autowired
    protected InvoiceRepository invoiceRepository;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        invoiceRepository.deleteAll();
        subscriptionRepository.deleteAll();
        correctionRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        userRepository.deleteAll();
        attendanceRepository.deleteAll();
        employeeShiftRepository.deleteAll();
        shiftRepository.deleteAll();
        employeeRepository.deleteAll();
        departmentRepository.deleteAll();
        positionRepository.deleteAll();
        locationRepository.deleteAll();
        companySettingsRepository.deleteAll();
        companyRepository.deleteAll();
        planRepository.deleteAll();

        // Self-service registration subscribes new tenants to FREE, so the
        // catalogue must always offer it.
        freePlan();
    }

    /**
     * Plan every {@link #givenCompany} lands on: no ceilings and geofencing
     * included, so a test only meets a plan limit when it asks for one.
     */
    protected Plan unlimitedPlan() {
        return planRepository.findByCodeIgnoreCase("TEST_UNLIMITED")
                .orElseGet(() -> planRepository.save(Plan.builder()
                        .code("TEST_UNLIMITED")
                        .name("Test Unlimited")
                        .priceAmount(new java.math.BigDecimal("100000.00"))
                        .currency("IDR")
                        .billingPeriod(BillingPeriod.MONTHLY)
                        .geofenceIncluded(true)
                        .active(true)
                        .build()));
    }

    /** Mirrors the FREE plan seeded by Flyway, which self-service sign-up needs. */
    protected Plan freePlan() {
        return planRepository.findByCodeIgnoreCase("FREE")
                .orElseGet(() -> planRepository.save(Plan.builder()
                        .code("FREE")
                        .name("Free")
                        .priceAmount(java.math.BigDecimal.ZERO)
                        .currency("IDR")
                        .billingPeriod(BillingPeriod.MONTHLY)
                        .maxEmployees(10)
                        .maxLocations(1)
                        .maxUsers(15)
                        .geofenceIncluded(false)
                        .active(true)
                        .build()));
    }

    protected Plan givenPlan(String code, Integer maxEmployees, Integer maxLocations,
                             Integer maxUsers, boolean geofenceIncluded) {
        return planRepository.save(Plan.builder()
                .code(code)
                .name(code)
                .priceAmount(new java.math.BigDecimal("299000.00"))
                .currency("IDR")
                .billingPeriod(BillingPeriod.MONTHLY)
                .maxEmployees(maxEmployees)
                .maxLocations(maxLocations)
                .maxUsers(maxUsers)
                .geofenceIncluded(geofenceIncluded)
                .active(true)
                .build());
    }

    protected Subscription givenSubscription(Company company, Plan plan, SubscriptionStatus status) {
        LocalDate today = LocalDate.now(clock);
        return subscriptionRepository.save(Subscription.builder()
                .company(company)
                .plan(plan)
                .status(status)
                .startDate(today)
                .currentPeriodStart(today)
                .currentPeriodEnd(plan.getBillingPeriod().endOfPeriod(today))
                .autoRenew(true)
                .build());
    }

    /** Company on an explicit plan, for tests that exercise plan limits. */
    protected Company givenCompanyOnPlan(String code, Plan plan) {
        Company company = companyRepository.save(Company.builder()
                .name("PT " + code.toUpperCase())
                .code(code)
                .email("info@" + code + ".test")
                .timezone("Asia/Jakarta")
                .status(CompanyStatus.ACTIVE)
                .build());
        givenSubscription(company, plan, SubscriptionStatus.ACTIVE);
        return company;
    }

    protected Company givenCompany(String code, CompanyStatus status) {
        Company company = companyRepository.save(Company.builder()
                .name("PT " + code.toUpperCase())
                .code(code)
                .email("info@" + code + ".test")
                .timezone("Asia/Jakarta")
                .status(status)
                .build());
        // Every tenant needs a subscription: the plan limit checks read from it.
        givenSubscription(company, unlimitedPlan(), SubscriptionStatus.ACTIVE);
        return company;
    }

    protected User givenUser(Company company, String email, UserRole role, UserStatus status) {
        return userRepository.save(User.builder()
                .company(company)
                .name(email)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(role)
                .status(status)
                .build());
    }

    protected Department givenDepartment(Company company, String name) {
        return departmentRepository.save(Department.builder()
                .company(company)
                .name(name)
                .description(name + " department")
                .build());
    }

    protected Position givenPosition(Company company, String name) {
        return positionRepository.save(Position.builder()
                .company(company)
                .name(name)
                .build());
    }

    protected Employee givenEmployee(Company company, String code, String name) {
        return givenEmployee(company, code, name, null, null, EmployeeStatus.ACTIVE);
    }

    protected Employee givenEmployee(Company company,
                                     String code,
                                     String name,
                                     Department department,
                                     Position position,
                                     EmployeeStatus status) {
        return employeeRepository.save(Employee.builder()
                .company(company)
                .employeeCode(code)
                .name(name)
                .email(code.toLowerCase() + "@" + company.getCode() + ".test")
                .department(department)
                .position(position)
                .joinDate(LocalDate.of(2026, 1, 15))
                .status(status)
                .build());
    }

    protected Shift givenShift(Company company,
                               String name,
                               LocalTime startTime,
                               LocalTime endTime,
                               int lateTolerance,
                               int earlyLeaveTolerance) {
        return shiftRepository.save(Shift.builder()
                .company(company)
                .name(name)
                .startTime(startTime)
                .endTime(endTime)
                .lateToleranceMinutes(lateTolerance)
                .earlyLeaveToleranceMinutes(earlyLeaveTolerance)
                .build());
    }

    protected EmployeeShift givenRoster(Company company, Employee employee, Shift shift, LocalDate date) {
        return employeeShiftRepository.save(EmployeeShift.builder()
                .company(company)
                .employee(employee)
                .shift(shift)
                .shiftDate(date)
                .build());
    }

    /**
     * Creates an employee together with the login account it needs to record
     * attendance, and returns that account's e-mail.
     */
    protected String givenEmployeeWithAccount(Company company, String code, String name) {
        Employee employee = givenEmployee(company, code, name);
        String email = code.toLowerCase() + "@" + company.getCode() + ".test";
        User account = givenUser(company, email, UserRole.EMPLOYEE, UserStatus.ACTIVE);
        account.setEmployee(employee);
        userRepository.save(account);
        return email;
    }

    /** Enables geofencing for the tenant and returns the stored settings. */
    protected CompanySettings givenGeofenceEnabled(Company company, boolean enabled) {
        CompanySettings settings = companySettingsRepository.findByCompany_Id(company.getId())
                .orElseGet(() -> CompanySettings.builder().company(company).build());
        settings.setGeofenceEnabled(enabled);
        return companySettingsRepository.save(settings);
    }

    protected Location givenLocation(Company company,
                                     String name,
                                     String latitude,
                                     String longitude,
                                     int radiusMeter) {
        return locationRepository.save(Location.builder()
                .company(company)
                .name(name)
                .latitude(new java.math.BigDecimal(latitude))
                .longitude(new java.math.BigDecimal(longitude))
                .radiusMeter(radiusMeter)
                .active(true)
                .build());
    }

    /** Logs in through the real endpoint and returns the access token. */
    protected String loginAndGetAccessToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json(body).path("data").path("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
