-- =====================================================================
-- Phase 2 : departments, positions, employees
-- Every table carries company_id; uniqueness is per tenant, never global.
-- =====================================================================

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255)     NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_departments PRIMARY KEY (id),
    CONSTRAINT uk_departments_company_name UNIQUE (company_id, name),
    CONSTRAINT fk_departments_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_departments_company ON departments (company_id);

CREATE TABLE positions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255)     NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_positions PRIMARY KEY (id),
    CONSTRAINT uk_positions_company_name UNIQUE (company_id, name),
    CONSTRAINT fk_positions_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_positions_company ON positions (company_id);

CREATE TABLE employees (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    company_id    BIGINT       NOT NULL,
    employee_code VARCHAR(50)  NOT NULL,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(150)     NULL,
    phone         VARCHAR(30)      NULL,
    department_id BIGINT           NULL,
    position_id   BIGINT           NULL,
    join_date     DATE             NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_employees PRIMARY KEY (id),
    CONSTRAINT uk_employees_company_code UNIQUE (company_id, employee_code),
    CONSTRAINT fk_employees_company    FOREIGN KEY (company_id)    REFERENCES companies (id),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_employees_position   FOREIGN KEY (position_id)   REFERENCES positions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_employees_company        ON employees (company_id);
CREATE INDEX idx_employees_company_status ON employees (company_id, status);
CREATE INDEX idx_employees_department     ON employees (department_id);
CREATE INDEX idx_employees_position       ON employees (position_id);
CREATE INDEX idx_employees_company_name   ON employees (company_id, name);

-- Login account <-> employee link (users.employee_id in the target schema).
ALTER TABLE users ADD COLUMN employee_id BIGINT NULL AFTER company_id;
ALTER TABLE users ADD CONSTRAINT uk_users_employee UNIQUE (employee_id);
ALTER TABLE users ADD CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees (id);
