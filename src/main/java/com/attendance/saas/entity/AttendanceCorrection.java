package com.attendance.saas.entity;

import com.attendance.saas.entity.enums.RequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request to fix a day the employee forgot to check in or out.
 *
 * <p>{@code requestedCheckIn} / {@code requestedCheckOut} are wall-clock times
 * in the company timezone. Once approved, whatever the attendance held before
 * is copied into {@code previousCheckIn} / {@code previousCheckOut}, so the
 * change is always reconstructable.
 */
@Entity
@Table(name = "attendance_corrections")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class AttendanceCorrection extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Filled once the day's attendance row exists. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id")
    private Attendance attendance;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "requested_check_in")
    private LocalTime requestedCheckIn;

    @Column(name = "requested_check_out")
    private LocalTime requestedCheckOut;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "previous_check_in")
    private Instant previousCheckIn;

    @Column(name = "previous_check_out")
    private Instant previousCheckOut;
}
