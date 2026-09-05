package com.attendance.saas.service;

import com.attendance.saas.dto.employee.CreateEmployeeAccountRequest;
import com.attendance.saas.dto.employee.EmployeeAccountResponse;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.UserStatus;
import com.attendance.saas.exception.BadRequestException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ForbiddenException;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Grants an employee a login account. Kept apart from {@link EmployeeService}
 * so employee master data and credential handling do not share a class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeAccountService {

    /** Roles a company admin may hand out; never SUPER_ADMIN or COMPANY_ADMIN. */
    private static final Set<UserRole> GRANTABLE_ROLES =
            Set.of(UserRole.EMPLOYEE, UserRole.SUPERVISOR, UserRole.HR);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeService employeeService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final PlanLimitService planLimitService;

    @Transactional
    public EmployeeAccountResponse createAccount(Long employeeId, CreateEmployeeAccountRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = employeeService.requireInCompany(employeeId, companyId);

        planLimitService.assertCanAddUser(companyId);

        if (!GRANTABLE_ROLES.contains(request.role())) {
            throw new ForbiddenException(
                    ErrorCode.ROLE_NOT_GRANTABLE,
                    "Role yang diperbolehkan hanya EMPLOYEE, SUPERVISOR, atau HR");
        }
        if (!employee.isActive()) {
            throw new BadRequestException(
                    ErrorCode.EMPLOYEE_INACTIVE,
                    "Karyawan tidak aktif sehingga tidak dapat dibuatkan akun");
        }
        if (userRepository.existsByEmployee_Id(employeeId)) {
            throw new ConflictException(
                    ErrorCode.EMPLOYEE_ALREADY_HAS_ACCOUNT, "Karyawan ini sudah memiliki akun");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(
                    ErrorCode.USER_EMAIL_ALREADY_EXISTS, "Email sudah terdaftar sebagai akun lain");
        }

        User user = User.builder()
                .company(companyRepository.getReferenceById(companyId))
                .employee(employee)
                .name(employee.getName())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(user);

        auditService.record(AuditAction.CREATE, "User", user.getId(),
                "Membuat akun %s untuk karyawan %s".formatted(
                        user.getRole(), employee.getEmployeeCode()));
        log.info("Account {} created for employee {} in company {}",
                user.getId(), employeeId, companyId);

        return new EmployeeAccountResponse(user.getId(), employeeId, user.getEmail(), user.getRole());
    }
}
