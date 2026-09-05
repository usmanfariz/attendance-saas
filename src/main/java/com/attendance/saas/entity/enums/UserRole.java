package com.attendance.saas.entity.enums;

/**
 * Platform roles. {@link #SUPER_ADMIN} is the only cross-tenant role; every
 * other role is always bound to exactly one company.
 */
public enum UserRole {

    SUPER_ADMIN,
    COMPANY_ADMIN,
    HR,
    SUPERVISOR,
    EMPLOYEE;

    public static final String ROLE_PREFIX = "ROLE_";

    public String authority() {
        return ROLE_PREFIX + name();
    }

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN;
    }
}
