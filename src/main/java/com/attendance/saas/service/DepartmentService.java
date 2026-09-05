package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.department.DepartmentRequest;
import com.attendance.saas.dto.department.DepartmentResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Department;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.DepartmentMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.DepartmentRepository;
import com.attendance.saas.repository.EmployeeRepository;
import com.attendance.saas.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Departments are tenant-scoped: the company always comes from the caller's
 * token, never from the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentMapper departmentMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> search(String keyword, Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        String normalized = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<Department> page = departmentRepository.search(companyId, normalized, pageable);
        return PageResponse.of(page, this::toResponseWithCount);
    }

    /** Flat list for dropdowns, without pagination noise. */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listAll() {
        return departmentRepository.findByCompany_IdOrderByNameAsc(SecurityUtils.requireCompanyId())
                .stream()
                .map(this::toResponseWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long departmentId) {
        return toResponseWithCount(load(departmentId, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        String name = request.name().trim();

        if (departmentRepository.existsByCompany_IdAndNameIgnoreCase(companyId, name)) {
            throw new ConflictException(
                    ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS, "Nama departemen sudah digunakan");
        }

        Department department = Department.builder()
                .company(companyReference(companyId))
                .name(name)
                .description(trimToNull(request.description()))
                .build();
        departmentRepository.save(department);

        auditService.record(AuditAction.CREATE, "Department", department.getId(), "Menambah departemen " + name);
        log.info("Department {} created in company {}", department.getId(), companyId);
        return departmentMapper.toResponse(department, 0);
    }

    @Transactional
    public DepartmentResponse update(Long departmentId, DepartmentRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Department department = load(departmentId, companyId);
        String name = request.name().trim();

        if (departmentRepository.existsByCompany_IdAndNameIgnoreCaseAndIdNot(companyId, name, departmentId)) {
            throw new ConflictException(
                    ErrorCode.DEPARTMENT_NAME_ALREADY_EXISTS, "Nama departemen sudah digunakan");
        }

        department.setName(name);
        department.setDescription(trimToNull(request.description()));

        auditService.record(AuditAction.UPDATE, "Department", departmentId, "Mengubah departemen " + name);
        log.info("Department {} updated in company {}", departmentId, companyId);
        return toResponseWithCount(department);
    }

    /**
     * Hard delete, allowed only while no employee still points at the
     * department — attendance history must never lose its context.
     */
    @Transactional
    public void delete(Long departmentId) {
        Long companyId = SecurityUtils.requireCompanyId();
        Department department = load(departmentId, companyId);

        long inUse = employeeRepository.countByDepartment_Id(departmentId);
        if (inUse > 0) {
            throw new ConflictException(
                    ErrorCode.DEPARTMENT_IN_USE,
                    "Departemen masih dipakai oleh " + inUse + " karyawan");
        }

        departmentRepository.delete(department);
        auditService.record(AuditAction.DELETE, "Department", departmentId, "Menghapus departemen " + department.getName());
        log.info("Department {} deleted from company {}", departmentId, companyId);
    }

    /** Tenant-safe loader reused by other services. */
    @Transactional(readOnly = true)
    public Department requireInCompany(Long departmentId, Long companyId) {
        return load(departmentId, companyId);
    }

    private Department load(Long departmentId, Long companyId) {
        return departmentRepository.findByIdAndCompany_Id(departmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.DEPARTMENT_NOT_FOUND, "Departemen tidak ditemukan"));
    }

    private DepartmentResponse toResponseWithCount(Department department) {
        return departmentMapper.toResponse(
                department, employeeRepository.countByDepartment_Id(department.getId()));
    }

    private Company companyReference(Long companyId) {
        return companyRepository.getReferenceById(companyId);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
