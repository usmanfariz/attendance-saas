package com.attendance.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Roster entry: the shift an employee is scheduled to work on one business
 * date. At most one entry per employee per date.
 */
@Entity
@Table(name = "employee_shifts")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class EmployeeShift extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    /** Business date the shift starts on, in the company timezone. */
    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;
}
