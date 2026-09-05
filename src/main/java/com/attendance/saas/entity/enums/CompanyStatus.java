package com.attendance.saas.entity.enums;

public enum CompanyStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
