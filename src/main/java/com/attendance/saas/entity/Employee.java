package com.attendance.saas.entity;

import com.attendance.saas.entity.enums.EmployeeStatus;
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

import java.time.LocalDate;

/**
 * A person employed by a tenant. Distinct from {@link User}, which is the login
 * account: an employee may exist without ever being able to sign in.
 */
@Entity
@Table(name = "employees")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Employee extends TenantEntity {

    /** Human-facing identifier, unique within the company. */
    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    public boolean isActive() {
        return status != null && status.isActive();
    }
}
