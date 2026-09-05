package com.attendance.saas.service;

import com.attendance.saas.dto.common.PageResponse;
import com.attendance.saas.dto.correction.AttendanceCorrectionCreateRequest;
import com.attendance.saas.dto.correction.AttendanceCorrectionResponse;
import com.attendance.saas.dto.leave.LeaveReviewRequest;
import com.attendance.saas.entity.Attendance;
import com.attendance.saas.entity.AttendanceCorrection;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.Employee;
import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.AttendanceStatus;
import com.attendance.saas.entity.enums.AuditAction;
import com.attendance.saas.entity.enums.RequestStatus;
import com.attendance.saas.exception.BusinessException;
import com.attendance.saas.exception.ConflictException;
import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ForbiddenException;
import com.attendance.saas.exception.ResourceNotFoundException;
import com.attendance.saas.mapper.AttendanceCorrectionMapper;
import com.attendance.saas.repository.AttendanceCorrectionRepository;
import com.attendance.saas.repository.AttendanceRepository;
import com.attendance.saas.repository.CompanyRepository;
import com.attendance.saas.repository.UserRepository;
import com.attendance.saas.security.SecurityUtils;
import com.attendance.saas.specification.AttendanceCorrectionSpecifications;
import com.attendance.saas.util.ShiftWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Requests to fix a day the employee forgot to check in or out, and the review
 * that applies them.
 *
 * <p>Approving recomputes lateness, early leave and worked minutes from the
 * corrected times, and keeps the previous values on the correction so the
 * change stays reconstructable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionService {

    private final AttendanceCorrectionRepository correctionRepository;
    private final AttendanceRepository attendanceRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final ShiftScheduleResolver scheduleResolver;
    private final AttendanceCalculator calculator;
    private final AttendanceCorrectionMapper correctionMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public AttendanceCorrectionResponse create(AttendanceCorrectionCreateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        Employee employee = employeeService.requireOwnEmployee();
        Company company = loadCompany(companyId);

        if (request.checkIn() == null && request.checkOut() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_CORRECTION,
                    "Minimal salah satu dari check-in atau check-out harus diisi");
        }

        LocalDate today = clock.instant().atZone(company.zoneId()).toLocalDate();
        if (request.attendanceDate().isAfter(today)) {
            throw new BusinessException(
                    ErrorCode.INVALID_CORRECTION,
                    "Tidak dapat mengajukan koreksi untuk tanggal yang belum terjadi");
        }

        if (correctionRepository.existsByCompany_IdAndEmployee_IdAndAttendanceDateAndStatus(
                companyId, employee.getId(), request.attendanceDate(), RequestStatus.PENDING)) {
            throw new ConflictException(
                    ErrorCode.CORRECTION_ALREADY_EXISTS,
                    "Sudah ada pengajuan koreksi yang menunggu persetujuan untuk tanggal tersebut");
        }

        Attendance attendance = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(
                        companyId, employee.getId(), request.attendanceDate())
                .orElse(null);

        AttendanceCorrection correction = AttendanceCorrection.builder()
                .company(companyRepository.getReferenceById(companyId))
                .employee(employee)
                .attendance(attendance)
                .attendanceDate(request.attendanceDate())
                .requestedCheckIn(request.checkIn())
                .requestedCheckOut(request.checkOut())
                .reason(request.reason().trim())
                .status(RequestStatus.PENDING)
                .build();
        correctionRepository.save(correction);

        auditService.record(AuditAction.CREATE, "AttendanceCorrection", correction.getId(),
                "Pengajuan koreksi absensi " + request.attendanceDate());
        log.info("Correction {} requested by employee {} for {}",
                correction.getId(), employee.getId(), request.attendanceDate());
        return correctionMapper.toResponse(correction);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceCorrectionResponse> search(Long employeeId,
                                                             RequestStatus status,
                                                             LocalDate startDate,
                                                             LocalDate endDate,
                                                             Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (employeeId != null) {
            employeeService.requireInCompany(employeeId, companyId);
        }
        return PageResponse.of(
                correctionRepository.findAll(
                        specification(companyId, employeeId, status, startDate, endDate), pageable),
                correctionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceCorrectionResponse> myRequests(RequestStatus status, Pageable pageable) {
        Long companyId = SecurityUtils.requireCompanyId();
        Long employeeId = employeeService.requireOwnEmployee().getId();
        return PageResponse.of(
                correctionRepository.findAll(
                        specification(companyId, employeeId, status, null, null), pageable),
                correctionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AttendanceCorrectionResponse getById(Long correctionId) {
        return correctionMapper.toResponse(load(correctionId, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public AttendanceCorrectionResponse approve(Long correctionId, LeaveReviewRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        AttendanceCorrection correction = loadPending(correctionId, companyId);
        User reviewer = requireReviewer(correction);
        Company company = loadCompany(companyId);

        applyToAttendance(company, correction);

        correction.setStatus(RequestStatus.APPROVED);
        applyReview(correction, reviewer, request.note());

        auditService.record(AuditAction.APPROVE, "AttendanceCorrection", correctionId,
                "Menyetujui koreksi absensi %s untuk %s".formatted(
                        correction.getAttendanceDate(), correction.getEmployee().getEmployeeCode()));
        log.info("Correction {} approved by user {}", correctionId, reviewer.getId());
        return correctionMapper.toResponse(correction);
    }

    @Transactional
    public AttendanceCorrectionResponse reject(Long correctionId, LeaveReviewRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        AttendanceCorrection correction = loadPending(correctionId, companyId);
        User reviewer = requireReviewer(correction);

        if (!StringUtils.hasText(request.note())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Alasan penolakan wajib diisi");
        }

        correction.setStatus(RequestStatus.REJECTED);
        applyReview(correction, reviewer, request.note());

        auditService.record(AuditAction.REJECT, "AttendanceCorrection", correctionId,
                "Menolak koreksi absensi " + correction.getAttendanceDate());
        log.info("Correction {} rejected by user {}", correctionId, reviewer.getId());
        return correctionMapper.toResponse(correction);
    }

    /**
     * Writes the corrected times onto the attendance row, creating it when the
     * employee never checked in at all, and recomputes every derived minute
     * count against the shift for that date.
     */
    private void applyToAttendance(Company company, AttendanceCorrection correction) {
        Employee employee = correction.getEmployee();
        LocalDate date = correction.getAttendanceDate();
        ZoneId zone = company.zoneId();

        Attendance attendance = attendanceRepository
                .findByCompany_IdAndEmployee_IdAndAttendanceDate(company.getId(), employee.getId(), date)
                .orElseGet(() -> Attendance.builder()
                        .company(company)
                        .employee(employee)
                        .attendanceDate(date)
                        .status(AttendanceStatus.PRESENT)
                        .build());

        // Audit trail: whatever the row held before the correction landed.
        correction.setPreviousCheckIn(attendance.getCheckIn());
        correction.setPreviousCheckOut(attendance.getCheckOut());

        Optional<ShiftWindow> window = scheduleResolver.resolveForDate(company, employee, date);
        window.ifPresent(shiftWindow -> attendance.setShift(shiftWindow.shift()));

        Instant checkIn = correction.getRequestedCheckIn() != null
                ? toInstant(date, correction.getRequestedCheckIn(), zone)
                : attendance.getCheckIn();

        Instant checkOut = correction.getRequestedCheckOut() != null
                ? resolveCheckOut(date, correction.getRequestedCheckOut(), checkIn, zone)
                : attendance.getCheckOut();

        if (checkIn != null && checkOut != null && checkOut.isBefore(checkIn)) {
            throw new BusinessException(
                    ErrorCode.INVALID_CORRECTION, "Waktu check-out tidak boleh mendahului check-in");
        }

        attendance.setCheckIn(checkIn);
        attendance.setCheckOut(checkOut);
        attendance.setWorkMinutes(calculator.workMinutes(checkIn, checkOut));

        if (window.isPresent()) {
            ShiftWindow shiftWindow = window.get();
            int lateMinutes = checkIn == null ? 0 : calculator.lateMinutes(shiftWindow, checkIn);
            attendance.setLateMinutes(lateMinutes);
            attendance.setEarlyLeaveMinutes(
                    checkOut == null ? 0 : calculator.earlyLeaveMinutes(shiftWindow, checkOut));
            attendance.setStatus(calculator.statusFor(lateMinutes));
        } else {
            // No roster for that date: record the times without judging lateness.
            attendance.setLateMinutes(0);
            attendance.setEarlyLeaveMinutes(0);
            attendance.setStatus(AttendanceStatus.PRESENT);
        }

        attendance.setNotes("Koreksi absensi: " + correction.getReason());
        attendanceRepository.save(attendance);
        correction.setAttendance(attendance);
    }

    /**
     * A check-out earlier in the day than the check-in belongs to the next
     * calendar day — that is how a night shift ends.
     */
    private Instant resolveCheckOut(LocalDate date, LocalTime time, Instant checkIn, ZoneId zone) {
        Instant sameDay = toInstant(date, time, zone);
        if (checkIn != null && sameDay.isBefore(checkIn)) {
            return toInstant(date.plusDays(1), time, zone);
        }
        return sameDay;
    }

    private Instant toInstant(LocalDate date, LocalTime time, ZoneId zone) {
        return date.atTime(time).atZone(zone).toInstant();
    }

    private Specification<AttendanceCorrection> specification(Long companyId,
                                                              Long employeeId,
                                                              RequestStatus status,
                                                              LocalDate startDate,
                                                              LocalDate endDate) {
        return AttendanceCorrectionSpecifications.ofCompany(companyId)
                .and(AttendanceCorrectionSpecifications.ofEmployee(employeeId))
                .and(AttendanceCorrectionSpecifications.hasStatus(status))
                .and(AttendanceCorrectionSpecifications.dateFrom(startDate))
                .and(AttendanceCorrectionSpecifications.dateTo(endDate))
                .and(AttendanceCorrectionSpecifications.withAssociationsFetched());
    }

    private AttendanceCorrection load(Long correctionId, Long companyId) {
        return correctionRepository.findByIdAndCompany_Id(correctionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CORRECTION_NOT_FOUND, "Pengajuan koreksi tidak ditemukan"));
    }

    private AttendanceCorrection loadPending(Long correctionId, Long companyId) {
        AttendanceCorrection correction = load(correctionId, companyId);
        if (!correction.getStatus().isPending()) {
            throw new ConflictException(
                    ErrorCode.CORRECTION_NOT_PENDING,
                    "Pengajuan sudah berstatus " + correction.getStatus());
        }
        return correction;
    }

    private User requireReviewer(AttendanceCorrection correction) {
        Long userId = SecurityUtils.requireUserId();
        User reviewer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "User tidak ditemukan"));

        Long reviewerEmployeeId = reviewer.getEmployeeId();
        if (reviewerEmployeeId != null
                && reviewerEmployeeId.equals(correction.getEmployee().getId())) {
            throw new ForbiddenException(
                    ErrorCode.CANNOT_REVIEW_OWN_REQUEST,
                    "Anda tidak dapat menyetujui koreksi Anda sendiri");
        }
        return reviewer;
    }

    private void applyReview(AttendanceCorrection correction, User reviewer, String note) {
        correction.setReviewedBy(reviewer);
        correction.setReviewedAt(clock.instant());
        correction.setReviewNote(StringUtils.hasText(note) ? note.trim() : null);
    }

    private Company loadCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.COMPANY_NOT_FOUND, "Perusahaan tidak ditemukan"));
    }
}
