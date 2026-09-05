package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.position.PositionRequest;
import com.attendance.saas.dto.position.PositionResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Position;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.PositionMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.repository.PositionRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final PositionMapper positionMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<PositionResponse> search(String keyword, Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<Position> page = positionRepository.search(companyId, normalized, pageable);
        return PageResponse.of(page, this::toResponseWithCount);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> listAll() {
        return positionRepository.findByCompany_IdOrderByNameAsc(SecurityUtils.requireCompanyId())
                .stream()
                .map(this::toResponseWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionResponse getById(Long positionId) {
        return toResponseWithCount(load(positionId, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public PositionResponse create(PositionRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        String name = request.name().trim();

        if (positionRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new ConflictException(
                    ErrorCode.POSITION_NAME_ALREADY_EXISTS, "Nama posisi sudah digunakan");
        }

        Position position = Position.builder()
                .company(companyReference(companyId))
                .name(name)
                .description(trimToNull(request.description()))
                .build();
        positionRepository.save(position);

        auditService.record(AuditAction.CREATE, "Position", position.getId(), "Menambah posisi " + name);
        log.info("Position {} created in company {}", position.getId(), companyId);
        return positionMapper.toResponse(position, 0);
    }

    @Transactional
    public PositionResponse update(Long positionId, PositionRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Position position = load(positionId, companyId);
        String name = request.name().trim();

        if (positionRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, name, positionId)) {
            throw new ConflictException(
                    ErrorCode.POSITION_NAME_ALREADY_EXISTS, "Nama posisi sudah digunakan");
        }

        position.setName(name);
        position.setDescription(trimToNull(request.description()));

        auditService.record(AuditAction.UPDATE, "Position", positionId, "Mengubah posisi " + name);
        log.info("Position {} updated in company {}", positionId, companyId);
        return toResponseWithCount(position);
    }

    @Transactional
    public void delete(Long positionId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Position position = load(positionId, companyId);

        long inUse = employeeRepository.countByPosition_Id(positionId);
        if (inUse > 0) {
            throw new ConflictException(
                    ErrorCode.POSITION_IN_USE, "Posisi masih dipakai oleh " + inUse + " karyawan");
        }

        positionRepository.delete(position);
        auditService.record(AuditAction.DELETE, "Position", positionId, "Menghapus posisi " + position.getName());
        log.info("Position {} deleted from company {}", positionId, companyId);
    }

    @Transactional(readOnly = true)
    public Position requireInCompany(Long positionId, Long companyId) {
        return load(positionId, companyId);
    }

    private Position load(Long positionId, Long companyId) {
        return positionRepository.findByIdAndCompany_Id(positionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.POSITION_NOT_FOUND, "Posisi tidak ditemukan"));
    }

    private PositionResponse toResponseWithCount(Position position) {
        return positionMapper.toResponse(
                position, employeeRepository.countByPosition_Id(position.getId()));
    }

    private Company companyReference(Long companyId) {
        return companyRepository.getReferenceById(companyId);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
