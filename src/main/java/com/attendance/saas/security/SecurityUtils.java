package com.attendance.saas.security;

import com.attendance.saas.exception.ErrorCode;
import com.attendance.saas.exception.ForbiddenException;
import com.attendance.saas.exception.UnauthorizedException;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Single entry point for reading the caller identity and the tenant it is
 * scoped to. Services must resolve {@code company_id} through here rather than
 * from client input.
 */
@UtilityClass
public class SecurityUtils {

    public Optional<UserPrincipal> getCurrentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public UserPrincipal requirePrincipal() {
        return getCurrentPrincipal()
                .orElseThrow(() -> new UnauthorizedException(
                        ErrorCode.UNAUTHORIZED, "Autentikasi diperlukan"));
    }

    public Long requireUserId() {
        return requirePrincipal().id();
    }

    /**
     * Tenant of the caller. {@code SUPER_ADMIN} has no own tenant and must use
     * an explicit company path variable instead.
     */
    public Long requireCompanyId() {
        UserPrincipal principal = requirePrincipal();
        if (principal.companyId() == null) {
            throw new ForbiddenException(
                    ErrorCode.TENANT_NOT_RESOLVED,
                    "Akun ini tidak terikat pada perusahaan manapun");
        }
        return principal.companyId();
    }

    public boolean isSuperAdmin() {
        return getCurrentPrincipal().map(UserPrincipal::isSuperAdmin).orElse(false);
    }

    /**
     * Guards an explicitly supplied company id: only a super admin may target a
     * tenant other than its own.
     */
    public void assertCanAccessCompany(Long companyId) {
        if (isSuperAdmin()) {
            return;
        }
        Long ownCompanyId = requireCompanyId();
        if (!ownCompanyId.equals(companyId)) {
            throw new ForbiddenException(
                    ErrorCode.CROSS_TENANT_ACCESS_DENIED,
                    "Anda tidak memiliki akses ke data perusahaan tersebut");
        }
    }
}
