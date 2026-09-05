-- =====================================================================
-- Phase 3 : shifts, shift assignments, attendance
--
-- Time model
--   * shifts.start_time / end_time are wall-clock times in the COMPANY
--     timezone (companies.timezone), never the server timezone.
--   * A shift whose end_time is not after its start_time crosses midnight
--     (e.g. 22:00 -> 07:00) and its check-out falls on the next calendar day.
--   * attendances.attendance_date is the BUSINESS date the shift started on,
--     so one night shift is a single row even though it spans two dates.
--   * check_in / check_out are absolute instants stored in UTC.
-- =====================================================================

CREATE TABLE shifts (
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    company_id                   BIGINT       NOT NULL,
    name                         VARCHAR(100) NOT NULL,
    start_time                   TIME         NOT NULL,
    end_time                     TIME         NOT NULL,
    late_tolerance_minutes       INT          NOT NULL DEFAULT 0,
    early_leave_tolerance_minutes INT         NOT NULL DEFAULT 0,
    is_default                   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   DATETIME(6)  NOT NULL,
    updated_at                   DATETIME(6)  NOT NULL,
    CONSTRAINT pk_shifts PRIMARY KEY (id),
    CONSTRAINT uk_shifts_company_name UNIQUE (company_id, name),
    CONSTRAINT fk_shifts_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_shifts_company         ON shifts (company_id);
CREATE INDEX idx_shifts_company_default ON shifts (company_id, is_default);

-- Roster: which shift an employee works on a given business date.
CREATE TABLE employee_shifts (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    company_id  BIGINT      NOT NULL,
    employee_id BIGINT      NOT NULL,
    shift_id    BIGINT      NOT NULL,
    shift_date  DATE        NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_employee_shifts PRIMARY KEY (id),
    CONSTRAINT uk_employee_shifts_employee_date UNIQUE (employee_id, shift_date),
    CONSTRAINT fk_employee_shifts_company  FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_employee_shifts_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_employee_shifts_shift    FOREIGN KEY (shift_id)    REFERENCES shifts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_employee_shifts_company_date ON employee_shifts (company_id, shift_date);
CREATE INDEX idx_employee_shifts_shift        ON employee_shifts (shift_id);

CREATE TABLE attendances (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    company_id           BIGINT       NOT NULL,
    employee_id          BIGINT       NOT NULL,
    shift_id             BIGINT           NULL,
    attendance_date      DATE         NOT NULL,
    check_in             DATETIME(6)      NULL,
    check_out            DATETIME(6)      NULL,
    check_in_latitude    DECIMAL(10, 7)   NULL,
    check_in_longitude   DECIMAL(10, 7)   NULL,
    check_out_latitude   DECIMAL(10, 7)   NULL,
    check_out_longitude  DECIMAL(10, 7)   NULL,
    check_in_photo       VARCHAR(500)     NULL,
    check_out_photo      VARCHAR(500)     NULL,
    status               VARCHAR(20)  NOT NULL,
    late_minutes         INT          NOT NULL DEFAULT 0,
    early_leave_minutes  INT          NOT NULL DEFAULT 0,
    work_minutes         INT          NOT NULL DEFAULT 0,
    notes                VARCHAR(500)     NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    CONSTRAINT pk_attendances PRIMARY KEY (id),
    CONSTRAINT uk_attendances_employee_date UNIQUE (employee_id, attendance_date),
    CONSTRAINT fk_attendances_company  FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_attendances_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_attendances_shift    FOREIGN KEY (shift_id)    REFERENCES shifts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_attendances_company_date  ON attendances (company_id, attendance_date);
CREATE INDEX idx_attendances_employee_date ON attendances (employee_id, attendance_date);
CREATE INDEX idx_attendances_status        ON attendances (company_id, status);
