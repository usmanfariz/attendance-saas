-- =====================================================================
-- Bootstrap platform owner.
-- Email    : superadmin@attendance.local
-- Password : SuperAdmin123!  (BCrypt cost 12)
-- CHANGE THIS PASSWORD IMMEDIATELY AFTER THE FIRST LOGIN.
-- company_id is NULL: SUPER_ADMIN is the only cross-tenant role.
-- =====================================================================

INSERT INTO users (company_id, name, email, password, role, status, created_at, updated_at)
SELECT NULL,
       'Super Administrator',
       'superadmin@attendance.local',
       '$2a$12$63Ga5B9D71rxmYmCWZoEkOvG7Lnwt5DgXKdwPyT/eaA2wTHyP2Rb2',
       'SUPER_ADMIN',
       'ACTIVE',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM (SELECT id FROM users WHERE email = 'superadmin@attendance.local') AS existing
);
