package com.attendance.saas.service;

import com.attendance.saas.dto.company.CompanyResponse;
import com.attendance.saas.dto.company.CompanyStatusUpdateRequest;
import com.attendance.saas.dto.company.CompanyUpdateRequest;
import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.enums.CompanyStatus;
import com.attendance.saas.exception.BadRequestException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.CompanyMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    /** Super-admin only: the platform-wide tenant list. */
    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> search(String keyword, CompanyStatus status, Pageable pageable) {
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return PageResponse.of(
                companyRepository.search(normalized, status, pageable), companyMapper::toResponse);
    }

    /** Profile of the tenant the caller belongs to. */
    @Transactional(readOnly = true)
    public CompanyResponse getCurrentCompany() {
        return companyMapper.toResponse(loadCompany(SecurityUtils.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(Long companyId) {
        SecurityUtils.assertCanAccessCompany(companyId);
        return companyMapper.toResponse(loadCompany(companyId));
    }

    @Transactional
    public CompanyResponse updateCurrentCompany(CompanyUpdateRequest request) {
        return update(SecurityUtils.requireCompanyId(), request);
    }

    @Transactional
    public CompanyResponse update(Long companyId, CompanyUpdateRequest request) {
        SecurityUtils.assertCanAccessCompany(companyId);
        Company company = loadCompany(companyId);

        String email = request.email().trim().toLowerCase();
        if (companyRepository.existsByEmailIgnoreCaseAndIdNot(email, companyId)) {
            throw new ConflictException(
                    ErrorCode.COMPANY_EMAIL_ALREADY_EXISTS, "Email perusahaan sudah digunakan");
        }

        company.setName(request.name().trim());
        company.setEmail(email);
        company.setPhone(request.phone());
        company.setAddress(request.address());
        company.setTimezone(validateTimezone(request.timezone()));

        log.info("Company {} profile updated", companyId);
        return companyMapper.toResponse(company);
    }

    /** Super-admin only: activate / suspend a tenant. */
    @Transactional
    public CompanyResponse updateStatus(Long companyId, CompanyStatusUpdateRequest request) {
        Company company = loadCompany(companyId);
        company.setStatus(request.status());
        log.info("Company {} status changed to {}", companyId, request.status());
        return companyMapper.toResponse(company);
    }

    private Company loadCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.COMPANY_NOT_FOUND, "Perusahaan tidak ditemukan"));
    }

    private String validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (DateTimeException ex) {
            throw new BadRequestException(
                    ErrorCode.INVALID_TIMEZONE, "Timezone tidak dikenali: " + timezone);
        }
    }
}
