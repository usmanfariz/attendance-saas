-- =====================================================================
-- Phase 5 : audit log and the leave quota the dashboard reports against
-- =====================================================================

-- Immutable trail of who did what. Rows are written, never updated.
-- user_id is nullable so a failed login attempt can still be recorded.
CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT           NULL,
    user_id     BIGINT           NULL,
    action      VARCHAR(30)  NOT NULL,
    entity      VARCHAR(50)      NULL,
    entity_id   BIGINT           NULL,
    description VARCHAR(500)     NULL,
    ip_address  VARCHAR(45)      NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_audit_logs_user    FOREIGN KEY (user_id)    REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_audit_logs_company_created ON audit_logs (company_id, created_at);
CREATE INDEX idx_audit_logs_company_action  ON audit_logs (company_id, action);
CREATE INDEX idx_audit_logs_user            ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_entity          ON audit_logs (entity, entity_id);

-- Annual leave allowance, the baseline the employee dashboard subtracts from.
-- 12 days is the statutory minimum in Indonesia.
ALTER TABLE company_settings
    ADD COLUMN annual_leave_quota_days INT NOT NULL DEFAULT 12;
