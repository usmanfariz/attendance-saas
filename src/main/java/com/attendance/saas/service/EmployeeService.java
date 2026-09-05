package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.employee.EmployeeCreateRequest;
import com.attendance.saas.dto.employee.EmployeeResponse;
import com.attendance.saas.dto.employee.EmployeeUpdateRequest;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.Position;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.EmployeeStatus;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.EmployeeMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.SecurityUtils;
import com.attendance.saas.security.UserPrincipal;
import com.attendance.saas.specification.EmployeeSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * Employee CRUD, search and pagination — all scoped to the caller's tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final EmployeeMapper employeeMapper;
    private final AuditService auditService;
    private final PlanLimitService planLimitService;

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> search(String keyword,
                                                 Long departmentId,
                                                 Long positionId,
                                                 EmployeeStatus status,
                                                 Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();

        Specification<Employee> specification = EmployeeSpecifications.ofCompany(companyId)
                .and(EmployeeSpecifications.keywordMatches(keyword))
                .and(EmployeeSpecifications.hasDepartment(departmentId))
                .and(EmployeeSpecifications.hasPosition(positionId))
                .and(EmployeeSpecifications.hasStatus(status))
                .and(EmployeeSpecifications.withAssociationsFetched());

        Page<Employee> page = employeeRepository.findAll(specification, pageable);
        Set<Long> withAccount = employeeIdsWithAccount(page.getContent());

        return PageResponse.of(page, employee ->
                employeeMapper.toResponse(employee, withAccount.contains(employee.getId())));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long employeeId) {
        Employee employee = load(employeeId, SecurityUtils.requireCompanyId());
        return employeeMapper.toResponse(employee, userRepository.existsByEmployee_Id(employeeId));
    }

    /** Profile of the employee behind the currently authenticated account. */
    @Transactional(readOnly = true)
    public EmployeeResponse getOwnProfile() {
        return employeeMapper.toResponse(requireOwnEmployee(), true);
    }

    /**
     * Employee record behind the current account, for callers that need the
     * entity itself (attendance, roster).
     *
     * @throws ResourceNotFoundException when the account has no employee link
     */
    @Transactional(readOnly = true)
    public Employee requireOwnEmployee() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        Employee employee = userRepository.findById(principal.id())
                .map(User::getEmployee)
                .orElse(null);

        if (employee == null) {
            throw new ResourceNotFoundException(
                    ErrorCode.NO_EMPLOYEE_PROFILE, "Akun ini tidak terhubung dengan data karyawan");
        }
        return employee;
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        String employeeCode = request.employeeCode().trim();
        String email = normalizeEmail(request.email());

        // Checked before any write, so a rejected request leaves nothing behind.
        planLimitService.assertCanAddEmployee(companyId);

        if (employeeRepository.existsByCompany_IdAndEmployeeCodeIgnoreCase(companyId, employeeCode)) {
            throw new ConflictException(
                    ErrorCode.EMPLOYEE_CODE_ALREADY_EXISTS, "Kode karyawan sudah digunakan");
        }
        if (email != null && employeeRepository.existsByCompany_IdAndEmailIgnoreCase(companyId, email)) {
            throw new ConflictException(
                    ErrorCode.EMPLOYEE_EMAIL_ALREADY_EXISTS, "Email karyawan sudah digunakan");
        }

        Employee employee = Employee.builder()
                .company(companyReference(companyId))
                .employeeCode(employeeCode)
                .name(request.name().trim())
                .email(email)
                .phone(trimToNull(request.phone()))
                .department(resolveDepartment(request.departmentId(), companyId))
                .position(resolvePosition(request.positionId(), companyId))
                .joinDate(request.joinDate())
                .status(request.status() == null ? EmployeeStatus.ACTIVE : request.status())
                .build();
        employeeRepository.save(employee);

        auditService.record(AuditAction.CREATE, "Employee", employee.getId(),
                "Menambah karyawan " + employee.getEmployeeCode());
        log.info("Employee {} ({}) created in company {}",
                employee.getId(), employee.getEmployeeCode(), companyId);
        return employeeMapper.toResponse(employee, false);
    }

    @Transactional
    public EmployeeResponse update(Long employeeId, EmployeeUpdateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = load(employeeId, companyId);
        String email = normalizeEmail(request.email());

        if (email != null
                && employeeRepository.existsByCompany_IdAndEmailIgnoreCaseAndIdNot(companyId, email, employeeId)) {
            throw new ConflictException(
                    ErrorCode.EMPLOYEE_EMAIL_ALREADY_EXISTS, "Email karyawan sudah digunakan");
        }

        employee.setName(request.name().trim());
        employee.setEmail(email);
        employee.setPhone(trimToNull(request.phone()));
        employee.setDepartment(resolveDepartment(request.departmentId(), companyId));
        employee.setPosition(resolvePosition(request.positionId(), companyId));
        employee.setJoinDate(request.joinDate());
        employee.setStatus(request.status());

        auditService.record(AuditAction.UPDATE, "Employee", employeeId,
                "Mengubah data karyawan " + employee.getEmployeeCode());
        log.info("Employee {} updated in company {}", employeeId, companyId);
        return employeeMapper.toResponse(employee, userRepository.existsByEmployee_Id(employeeId));
    }

    /**
     * Soft delete: the row stays so past and future attendance records keep a
     * valid owner, but the employee is marked as no longer employed.
     */
    @Transactional
    public EmployeeResponse deactivate(Long employeeId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = load(employeeId, companyId);

        employee.setStatus(EmployeeStatus.RESIGNED);
        auditService.record(AuditAction.DELETE, "Employee", employeeId,
                "Menonaktifkan karyawan " + employee.getEmployeeCode());
        log.info("Employee {} marked as RESIGNED in company {}", employeeId, companyId);

        return employeeMapper.toResponse(employee, userRepository.existsByEmployee_Id(employeeId));
    }

    /** Tenant-safe loader reused by the account and attendance services. */
    @Transactional(readOnly = true)
    public Employee requireInCompany(Long employeeId, Long companyId) {
        return load(employeeId, companyId);
    }

    private Employee load(Long employeeId, Long companyId) {
        return employeeRepository.findByIdAndCompany_Id(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.EMPLOYEE_NOT_FOUND, "Karyawan tidak ditemukan"));
    }

    private Set<Long> employeeIdsWithAccount(List<Employee> employees) {
        if (employees.isEmpty()) {
            return Set.of();
        }
        return userRepository.findEmployeeIdsWithAccount(
                employees.stream().map(Employee::getId).toList());
    }

    private Department resolveDepartment(Long departmentId, Long companyId) {
        return departmentId == null ? null : departmentService.requireInCompany(departmentId, companyId);
    }

    private Position resolvePosition(Long positionId, Long companyId) {
        return positionId == null ? null : positionService.requireInCompany(positionId, companyId);
    }

    private Company companyReference(Long companyId) {
        return companyRepository.getReferenceById(companyId);
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
