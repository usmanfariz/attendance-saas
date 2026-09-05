package com.attendance.saas.integration;

import com.attendance.saas.entity.User;
import com.attendance.saas.entity.enums.UserRole;
import com.attendance.saas.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the app with Flyway on and {@code ddl-auto=validate}, so the migration
 * scripts and the JPA mappings cannot drift apart unnoticed.
 *
 * <p>Runs on H2 in MySQL mode; the production MySQL 8 run is exercised by
 * {@code docker compose up}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@DisplayName("Flyway migration")
class FlywayMigrationIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("skema hasil migration cocok dengan mapping entity dan berisi seed super admin")
    void migrationMatchesEntityMappingAndSeedsSuperAdmin() {
        User superAdmin = userRepository.findByEmailIgnoreCase("superadmin@attendance.local").orElseThrow();

        assertThat(superAdmin.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(superAdmin.getCompanyId()).isNull();
        assertThat(superAdmin.getPassword()).startsWith("$2a$12$");
    }
}
