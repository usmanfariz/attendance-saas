package com.attendance.saas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "positions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Position extends TenantEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 255)
    private String description;
}
