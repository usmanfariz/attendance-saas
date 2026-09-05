package com.attendance.saas.mapper;

import com.attendance.saas.dto.auth.AuthenticatedUserResponse;
import com.attendance.saas.entity.Company;
import com.attendance.saas.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /** Never maps the password field. */
    public AuthenticatedUserResponse toAuthenticatedUser(User user) {
        Company company = user.getCompany();
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                company == null ? null : company.getId(),
                company == null ? null : company.getName(),
                company == null ? null : company.getCode(),
                company == null ? null : company.getTimezone());
    }
}
