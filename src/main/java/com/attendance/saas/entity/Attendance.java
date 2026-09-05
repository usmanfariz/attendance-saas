package com.attendance.saas.entity;

import com.attendance.saas.entity.enums.AttendanceStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One working day for one employee.
 *
 * <p>{@code attendanceDate} is the business date the shift <em>started</em> on,
 * in the company timezone. A night shift running 22:00 → 07:00 is therefore a
 * single row dated on the evening it began, even though the check-out instant
 * falls on the next calendar day.
 */
@Entity
@Table(name = "attendances")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Attendance extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Null when the day has no roster entry, e.g. a holiday record. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in")
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "check_in_latitude", precision = 10, scale = 7)
    private BigDecimal checkInLatitude;

    @Column(name = "check_in_longitude", precision = 10, scale = 7)
    private BigDecimal checkInLongitude;

    @Column(name = "check_out_latitude", precision = 10, scale = 7)
    private BigDecimal checkOutLatitude;

    @Column(name = "check_out_longitude", precision = 10, scale = 7)
    private BigDecimal checkOutLongitude;

    @Column(name = "check_in_photo", length = 500)
    private String checkInPhoto;

    @Column(name = "check_out_photo", length = 500)
    private String checkOutPhoto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "late_minutes", nullable = false)
    @Builder.Default
    private int lateMinutes = 0;

    @Column(name = "early_leave_minutes", nullable = false)
    @Builder.Default
    private int earlyLeaveMinutes = 0;

    @Column(name = "work_minutes", nullable = false)
    @Builder.Default
    private int workMinutes = 0;

    @Column(length = 500)
    private String notes;

    public boolean hasCheckedIn() {
        return checkIn != null;
    }

    public boolean hasCheckedOut() {
        return checkOut != null;
    }
}
