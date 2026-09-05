package com.attendance.saas.service;

import com.attendance.saas.dto.auth.RegisterCompanyRequest;
import com.attendance.saas.dto.company.RegisterCompanyResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.CompanySettings;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.entity.enums.UserStatus;
import com.attendance.saas.exception.BadRequestException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.mapper.CompanyMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.CompanySettingsRepository;
import com.attendance.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Tenant provisioning: creates the company and its first COMPANY_ADMIN in a
 * single transaction, so a tenant never exists without an owner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyRegistrationService {

    private static final String DEFAULT_TIMEZONE = "Asia/Jakarta";

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyMapper companyMapper;
    private final SubscriptionService subscriptionService;

    @Transactional
    public RegisterCompanyResponse register(RegisterCompanyRequest request) {
        String code = request.companyCode().trim().toLowerCase();
        String companyEmail = request.companyEmail().trim().toLowerCase();
        String adminEmail = request.adminEmail().trim().toLowerCase();

        if (companyRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException(
                    ErrorCode.COMPANY_CODE_ALREADY_EXISTS, "Kode perusahaan sudah digunakan");
        }
        if (companyRepository.existsByEmailIgnoreCase(companyEmail)) {
            throw new ConflictException(
                    ErrorCode.COMPANY_EMAIL_ALREADY_EXISTS, "Email perusahaan sudah terdaftar");
        }
        if (userRepository.existsByEmailIgnoreCase(adminEmail)) {
            throw new ConflictException(
                    ErrorCode.USER_EMAIL_ALREADY_EXISTS, "Email admin sudah terdaftar");
        }

        Company company = Company.builder()
                .name(request.companyName().trim())
                .code(code)
                .email(companyEmail)
                .phone(request.phone())
                .address(request.address())
                .timezone(resolveTimezone(request.timezone()))
                .status(CompanyStatus.ACTIVE)
                .build();
        companyRepository.save(company);

        // Geofencing starts off; the tenant turns it on once offices are defined.
        companySettingsRepository.save(CompanySettings.builder()
                .company(company)
                .geofenceEnabled(false)
                .build());

        // Every tenant must have a subscription: the limit checks read from it.
        subscriptionService.createDefaultSubscription(company);

        User admin = User.builder()
                .company(company)
                .name(request.adminName().trim())
                .email(adminEmail)
                .password(passwordEncoder.encode(request.adminPassword()))
                .role(UserRole.COMPANY_ADMIN)
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);

        log.info("Registered company {} (code={}) with admin user {}",
                company.getId(), company.getCode(), admin.getId());

        return new RegisterCompanyResponse(
                companyMapper.toResponse(company), admin.getId(), admin.getEmail());
    }

    private String resolveTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException ex) {
            throw new BadRequestException(
                    ErrorCode.INVALID_TIMEZONE, "Timezone tidak dikenali: " + timezone);
        }
    }
}
