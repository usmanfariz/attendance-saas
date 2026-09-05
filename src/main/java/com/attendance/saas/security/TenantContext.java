package com.attendance.saas.security;

/**
 * Per-request holder for the tenant resolved from the JWT.
 *
 * <p>The value never comes from a request body or query parameter, so a client
 * cannot talk itself into another tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_COMPANY_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setCompanyId(Long companyId) {
        CURRENT_COMPANY_ID.set(companyId);
    }

    public static Long getCompanyId() {
        return CURRENT_COMPANY_ID.get();
    }

    public static boolean hasCompany() {
        return CURRENT_COMPANY_ID.get() != null;
    }

    public static void clear() {
        CURRENT_COMPANY_ID.remove();
    }
}
