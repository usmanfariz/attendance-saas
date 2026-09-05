package com.attendance.saas.entity.enums;

public enum UserStatus {

    ACTIVE,
    INACTIVE,
    SUSPENDED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
