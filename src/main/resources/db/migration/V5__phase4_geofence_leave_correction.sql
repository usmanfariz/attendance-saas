-- =====================================================================
-- Phase 4 : company settings, office locations (geofence), leave
--           requests, attendance corrections
-- =====================================================================

-- One settings row per tenant. Kept out of `companies` so later phases can
-- add options without touching the tenant table itself.
CREATE TABLE company_settings (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    company_id       BIGINT      NOT NULL,
    geofence_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT pk_company_settings PRIMARY KEY (id),
    CONSTRAINT uk_company_settings_company UNIQUE (company_id),
    CONSTRAINT fk_company_settings_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every existing tenant starts with geofencing off, so enabling it stays an
-- explicit decision rather than a surprise after deploy.
INSERT INTO company_settings (company_id, geofence_enabled, created_at, updated_at)
SELECT c.id, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) FROM companies c;

CREATE TABLE locations (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    company_id    BIGINT         NOT NULL,
    name          VARCHAR(100)   NOT NULL,
    address       VARCHAR(255)       NULL,
    latitude      DECIMAL(10, 7) NOT NULL,
    longitude     DECIMAL(10, 7) NOT NULL,
    radius_meter  INT            NOT NULL DEFAULT 100,
    is_active     BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    CONSTRAINT pk_locations PRIMARY KEY (id),
    CONSTRAINT uk_locations_company_name UNIQUE (company_id, name),
    CONSTRAINT fk_locations_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_locations_company_active ON locations (company_id, is_active);

CREATE TABLE leave_requests (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    company_id       BIGINT       NOT NULL,
    employee_id      BIGINT       NOT NULL,
    leave_type       VARCHAR(20)  NOT NULL,
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    total_days       INT          NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by      BIGINT           NULL,
    reviewed_at      DATETIME(6)      NULL,
    review_note      VARCHAR(500)     NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT pk_leave_requests PRIMARY KEY (id),
    CONSTRAINT fk_leave_requests_company  FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_leave_requests_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_leave_requests_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_leave_requests_company_status ON leave_requests (company_id, status);
CREATE INDEX idx_leave_requests_employee_range ON leave_requests (employee_id, start_date, end_date);

-- Requested times are wall-clock in company time; the reviewer's decision and
-- the values the attendance held beforehand are kept as the audit trail.
CREATE TABLE attendance_corrections (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    company_id           BIGINT       NOT NULL,
    employee_id          BIGINT       NOT NULL,
    attendance_id        BIGINT           NULL,
    attendance_date      DATE         NOT NULL,
    requested_check_in   TIME             NULL,
    requested_check_out  TIME             NULL,
    reason               VARCHAR(500) NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by          BIGINT           NULL,
    reviewed_at          DATETIME(6)      NULL,
    review_note          VARCHAR(500)     NULL,
    previous_check_in    DATETIME(6)      NULL,
    previous_check_out   DATETIME(6)      NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    CONSTRAINT pk_attendance_corrections PRIMARY KEY (id),
    CONSTRAINT fk_corrections_company    FOREIGN KEY (company_id)    REFERENCES companies (id),
    CONSTRAINT fk_corrections_employee   FOREIGN KEY (employee_id)   REFERENCES employees (id),
    CONSTRAINT fk_corrections_attendance FOREIGN KEY (attendance_id) REFERENCES attendances (id),
    CONSTRAINT fk_corrections_reviewer   FOREIGN KEY (reviewed_by)   REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_corrections_company_status  ON attendance_corrections (company_id, status);
CREATE INDEX idx_corrections_employee_date   ON attendance_corrections (employee_id, attendance_date);
