package com.attendance.saas.security;

import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated caller, carrying the tenant it is allowed to touch.
 *
 * @param companyId {@code null} only for {@code SUPER_ADMIN}.
 */
public record UserPrincipal(
        Long id,
        String email,
        String name,
        String passwordHash,
        UserRole role,
        Long companyId,
        boolean active
) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPassword(),
                user.getRole(),
                user.getCompanyId(),
                user.isActive());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public boolean isSuperAdmin() {
        return role != null && role.isSuperAdmin();
    }
}
