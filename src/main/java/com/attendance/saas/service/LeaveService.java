package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.leave.LeaveRequestCreateRequest;
import com.attendance.saas.dto.leave.LeaveRequestResponse;
import com.attendance.saas.dto.leave.LeaveReviewRequest;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.LeaveRequest;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.LeaveType;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.RequestStatus;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ForbiddenException;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.LeaveMapper;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.LeaveRequestRepository;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.SecurityUtils;
import com.attendance.saas.specification.LeaveRequestSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Leave requests and their approval workflow.
 *
 * <p>Approving a request also materialises the attendance rows for the days it
 * covers, which is how "an employee on approved leave is not ABSENT" is made
 * true rather than merely stated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    private static final int MAX_LEAVE_DAYS = 90;

    private final LeaveRequestRepository leaveRequestRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final LeaveAttendanceMarker leaveAttendanceMarker;
    private final LeaveMapper leaveMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public LeaveRequestResponse create(LeaveRequestCreateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = employeeService.requireOwnEmployee();
        validateRange(request.startDate(), request.endDate());

        List<LeaveRequest> overlapping = leaveRequestRepository.findOverlapping(
                companyId, employee.getId(), request.startDate(), request.endDate(),
                List.of(RequestStatus.PENDING, RequestStatus.APPROVED));

        if (!overlapping.isEmpty()) {
            throw new ConflictException(
                    ErrorCode.LEAVE_ALREADY_EXISTS,
                    "Sudah ada pengajuan cuti yang beririsan pada tanggal tersebut");
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .company(companyRepository.getReferenceById(companyId))
                .employee(employee)
                .leaveType(request.leaveType())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .totalDays(inclusiveDays(request.startDate(), request.endDate()))
                .reason(request.reason().trim())
                .status(RequestStatus.PENDING)
                .build();
        leaveRequestRepository.save(leaveRequest);

        auditService.record(AuditAction.CREATE, "LeaveRequest", leaveRequest.getId(),
                "Pengajuan %s %s s/d %s".formatted(
                        request.leaveType(), request.startDate(), request.endDate()));
        log.info("Leave {} requested by employee {} ({} to {})",
                leaveRequest.getId(), employee.getId(), request.startDate(), request.endDate());
        return leaveMapper.toResponse(leaveRequest);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> search(Long employeeId,
                                                     Long departmentId,
                                                     RequestStatus status,
                                                     LeaveType leaveType,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (employeeId != null) {
            employeeService.requireInCompany(employeeId, companyId);
        }

        Specification<LeaveRequest> specification = LeaveRequestSpecifications.ofCompany(companyId)
                .and(LeaveRequestSpecifications.ofEmployee(employeeId))
                .and(LeaveRequestSpecifications.ofDepartment(departmentId))
                .and(LeaveRequestSpecifications.hasStatus(status))
                .and(LeaveRequestSpecifications.hasType(leaveType))
                .and(LeaveRequestSpecifications.overlapping(startDate, endDate))
                .and(LeaveRequestSpecifications.withAssociationsFetched());

        return PageResponse.of(
                leaveRequestRepository.findAll(specification, pageable), leaveMapper::toResponse);
    }

    /** History of the employee behind the current account. */
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> myRequests(RequestStatus status, Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        Long employeeId = employeeService.requireOwnEmployee().getId();

        Specification<LeaveRequest> specification = LeaveRequestSpecifications.ofCompany(companyId)
                .and(LeaveRequestSpecifications.ofEmployee(employeeId))
                .and(LeaveRequestSpecifications.hasStatus(status))
                .and(LeaveRequestSpecifications.withAssociationsFetched());

        return PageResponse.of(
                leaveRequestRepository.findAll(specification, pageable), leaveMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Long leaveId) {
        return leaveMapper.toResponse(load(leaveId, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public LeaveRequestResponse approve(Long leaveId, LeaveReviewRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        LeaveRequest leaveRequest = loadPending(leaveId, companyId);
        User reviewer = requireReviewer(leaveRequest);

        leaveRequest.setStatus(RequestStatus.APPROVED);
        applyReview(leaveRequest, reviewer, request.note());

        Company company = companyRepository.getReferenceById(companyId);
        leaveAttendanceMarker.markApproved(company, leaveRequest);

        auditService.record(AuditAction.APPROVE, "LeaveRequest", leaveId,
                "Menyetujui cuti karyawan " + leaveRequest.getEmployee().getEmployeeCode());
        log.info("Leave {} approved by user {}", leaveId, reviewer.getId());
        return leaveMapper.toResponse(leaveRequest);
    }

    @Transactional
    public LeaveRequestResponse reject(Long leaveId, LeaveReviewRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        LeaveRequest leaveRequest = loadPending(leaveId, companyId);
        User reviewer = requireReviewer(leaveRequest);

        if (!StringUtils.hasText(request.note())) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR, "Alasan penolakan wajib diisi");
        }

        leaveRequest.setStatus(RequestStatus.REJECTED);
        applyReview(leaveRequest, reviewer, request.note());

        auditService.record(AuditAction.REJECT, "LeaveRequest", leaveId,
                "Menolak cuti karyawan " + leaveRequest.getEmployee().getEmployeeCode());
        log.info("Leave {} rejected by user {}", leaveId, reviewer.getId());
        return leaveMapper.toResponse(leaveRequest);
    }

    /** Withdrawal by the requester; only their own pending request. */
    @Transactional
    public LeaveRequestResponse cancel(Long leaveId) {
        Long companyId = SecurityUtils.requireCompanyId();
        LeaveRequest leaveRequest = loadPending(leaveId, companyId);
        Employee employee = employeeService.requireOwnEmployee();

        if (!employee.getId().equals(leaveRequest.getEmployee().getId())) {
            throw new ForbiddenException(
                    ErrorCode.NOT_REQUEST_OWNER, "Anda hanya dapat membatalkan pengajuan sendiri");
        }

        leaveRequest.setStatus(RequestStatus.CANCELLED);
        leaveRequest.setReviewedAt(clock.instant());

        log.info("Leave {} cancelled by its requester", leaveId);
        return leaveMapper.toResponse(leaveRequest);
    }

    private LeaveRequest load(Long leaveId, Long companyId) {
        return leaveRequestRepository.findByIdAndCompany_Id(leaveId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.LEAVE_NOT_FOUND, "Pengajuan cuti tidak ditemukan"));
    }

    private LeaveRequest loadPending(Long leaveId, Long companyId) {
        LeaveRequest leaveRequest = load(leaveId, companyId);
        if (!leaveRequest.getStatus().isPending()) {
            throw new ConflictException(
                    ErrorCode.LEAVE_NOT_PENDING,
                    "Pengajuan sudah berstatus " + leaveRequest.getStatus());
        }
        return leaveRequest;
    }

    /** Nobody signs off their own absence, whatever their role. */
    private User requireReviewer(LeaveRequest leaveRequest) {
        Long userId = SecurityUtils.requireUserId();
        User reviewer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User tidak ditemukan"));

        Long reviewerEmployeeId = reviewer.getEmployeeId();
        if (reviewerEmployeeId != null
                && reviewerEmployeeId.equals(leaveRequest.getEmployee().getId())) {
            throw new ForbiddenException(
                    ErrorCode.CANNOT_REVIEW_OWN_REQUEST,
                    "Anda tidak dapat menyetujui pengajuan Anda sendiri");
        }
        return reviewer;
    }

    private void applyReview(LeaveRequest leaveRequest, User reviewer, String note) {
        leaveRequest.setReviewedBy(reviewer);
        leaveRequest.setReviewedAt(clock.instant());
        leaveRequest.setReviewNote(StringUtils.hasText(note) ? note.trim() : null);
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Tanggal selesai tidak boleh lebih awal dari tanggal mulai");
        }
        if (inclusiveDays(startDate, endDate) > MAX_LEAVE_DAYS) {
            throw new BusinessException(
                    ErrorCode.INVALID_DATE_RANGE,
                    "Pengajuan cuti maksimal " + MAX_LEAVE_DAYS + " hari");
        }
    }

    private int inclusiveDays(LocalDate startDate, LocalDate endDate) {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }
}
