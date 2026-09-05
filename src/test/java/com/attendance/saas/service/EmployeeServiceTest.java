package com.attendance.saas.service;

import com.attendance.saas.dto.employee.EmployeeCreateRequest;
import com.attendance.saas.dto.employee.EmployeeResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.exception.ApiException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.mapper.EmployeeMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceTest {

    private static final long COMPANY_ID = 10L;

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private DepartmentService departmentService;
    @Mock
    private PositionService positionService;
    @Mock
    private AuditService auditService;
    @Mock
    private PlanLimitService planLimitService;

    private EmployeeService employeeService;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder().name("PT Alpha").code("alpha").build();
        company.setId(COMPANY_ID);

        employeeService = new EmployeeService(
                employeeRepository, userRepository, companyRepository,
                departmentService, positionService, new EmployeeMapper(), auditService,
                planLimitService);

        when(companyRepository.getReferenceById(COMPANY_ID)).thenReturn(company);
        authenticateAs(UserRole.HR, COMPANY_ID);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("create menyimpan karyawan pada tenant milik pemanggil, bukan tenant dari request")
    void createBindsEmployeeToCallerTenant() {
        when(employeeRepository.existsByCompany_IdAndEmployeeCodeIgnoreCase(anyLong(), anyString()))
                .thenReturn(false);
        when(employeeRepository.existsByCompany_IdAndEmailIgnoreCase(anyLong(), anyString()))
                .thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(call -> call.getArgument(0));

        EmployeeResponse response = employeeService.create(new EmployeeCreateRequest(
                " EMP-001 ", " Siti Rahayu ", "SITI@Alpha.Test", " +62811 ",
                null, null, LocalDate.of(2026, 1, 15), null));

        assertThat(response.employeeCode()).isEqualTo("EMP-001");
        assertThat(response.name()).isEqualTo("Siti Rahayu");
        assertThat(response.email()).isEqualTo("siti@alpha.test");
        assertThat(response.status()).isEqualTo(EmployeeStatus.ACTIVE);
        assertThat(response.hasUserAccount()).isFalse();

        verify(employeeRepository).existsByCompany_IdAndEmployeeCodeIgnoreCase(COMPANY_ID, "EMP-001");
    }

    @Test
    @DisplayName("kode karyawan duplikat dalam satu tenant ditolak")
    void duplicateEmployeeCodeRejected() {
        when(employeeRepository.existsByCompany_IdAndEmployeeCodeIgnoreCase(COMPANY_ID, "EMP-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(new EmployeeCreateRequest(
                "EMP-001", "Siti", null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("departemen dari tenant lain tidak dapat dipasang ke karyawan")
    void departmentFromAnotherTenantIsRejected() {
        when(employeeRepository.existsByCompany_IdAndEmployeeCodeIgnoreCase(anyLong(), anyString()))
                .thenReturn(false);
        when(departmentService.requireInCompany(99L, COMPANY_ID))
                .thenThrow(new com.attendance.saas.exception.ResourceNotFoundException(
                        ErrorCode.DEPARTMENT_NOT_FOUND, "Departemen tidak ditemukan"));

        assertThatThrownBy(() -> employeeService.create(new EmployeeCreateRequest(
                "EMP-002", "Budi", null, null, 99L, null, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById selalu difilter dengan company id dari token")
    void getByIdIsScopedToTenant() {
        when(employeeRepository.findByIdAndCompany_Id(5L, COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(5L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPLOYEE_NOT_FOUND);

        verify(employeeRepository).findByIdAndCompany_Id(5L, COMPANY_ID);
    }

    @Test
    @DisplayName("delete bersifat soft: status menjadi RESIGNED, baris tidak dihapus")
    void deleteIsSoft() {
        Employee employee = Employee.builder()
                .company(company)
                .employeeCode("EMP-003")
                .name("Andi")
                .status(EmployeeStatus.ACTIVE)
                .build();
        employee.setId(3L);
        when(employeeRepository.findByIdAndCompany_Id(3L, COMPANY_ID)).thenReturn(Optional.of(employee));

        EmployeeResponse response = employeeService.deactivate(3L);

        assertThat(response.status()).isEqualTo(EmployeeStatus.RESIGNED);
        assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.RESIGNED);
        verify(employeeRepository, never()).delete(any(Employee.class));
    }

    @Test
    @DisplayName("mapper mengisi nama departemen dan posisi tanpa mengekspos entity")
    void mapperFillsDepartmentAndPositionNames() {
        Department department = Department.builder().company(company).name("Produksi").build();
        department.setId(1L);
        Employee employee = Employee.builder()
                .company(company)
                .employeeCode("EMP-004")
                .name("Rina")
                .department(department)
                .status(EmployeeStatus.ACTIVE)
                .build();
        employee.setId(4L);
        when(employeeRepository.findByIdAndCompany_Id(4L, COMPANY_ID)).thenReturn(Optional.of(employee));
        when(userRepository.existsByEmployee_Id(4L)).thenReturn(true);

        EmployeeResponse response = employeeService.getById(4L);

        assertThat(response.departmentId()).isEqualTo(1L);
        assertThat(response.departmentName()).isEqualTo("Produksi");
        assertThat(response.positionId()).isNull();
        assertThat(response.hasUserAccount()).isTrue();
    }

    private void authenticateAs(UserRole role, Long companyId) {
        UserPrincipal principal = new UserPrincipal(
                1L, "hr@alpha.test", "HR", null, role, companyId, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
