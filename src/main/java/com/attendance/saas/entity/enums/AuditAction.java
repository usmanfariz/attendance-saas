package com.attendance.saas.entity.enums;

/**
 * Activities worth keeping a trail of (specification section 19).
 */
public enum AuditAction {

    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    CREATE,
    UPDATE,
    DELETE,
    CHECK_IN,
    CHECK_OUT,
    APPROVE,
    REJECT
}
