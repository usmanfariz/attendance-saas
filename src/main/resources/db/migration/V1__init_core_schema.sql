-- =====================================================================
-- Phase 1 : core multi-tenant schema (companies, users, refresh tokens)
-- =====================================================================

CREATE TABLE companies (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    email       VARCHAR(150) NOT NULL,
    phone       VARCHAR(30)      NULL,
    address     VARCHAR(255)     NULL,
    timezone    VARCHAR(64)  NOT NULL DEFAULT 'Asia/Jakarta',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_companies PRIMARY KEY (id),
    CONSTRAINT uk_companies_code  UNIQUE (code),
    CONSTRAINT uk_companies_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_companies_status ON companies (status);

-- Email is globally unique: login is performed with e-mail only (no tenant
-- selector), therefore one address must resolve to exactly one user.
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT           NULL,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password      VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at DATETIME(6)      NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_users_company      ON users (company_id);
CREATE INDEX idx_users_company_role ON users (company_id, role);

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    company_id BIGINT          NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6)     NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
