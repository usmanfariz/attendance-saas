-- =====================================================================
-- Phase 6 : plans, subscriptions, invoices
--
-- `plans` is platform-level and has no company_id: it is the catalogue the
-- super admin curates. `subscriptions` and `invoices` belong to a tenant.
--
-- A NULL limit means unlimited. Storing NULL rather than a sentinel like -1
-- keeps "no ceiling" unambiguous in both SQL and Java.
-- =====================================================================

CREATE TABLE plans (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)    NOT NULL,
    name           VARCHAR(100)   NOT NULL,
    description    VARCHAR(255)       NULL,
    price_amount   DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    currency       VARCHAR(3)     NOT NULL DEFAULT 'IDR',
    billing_period VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY',
    max_employees  INT                NULL,
    max_locations  INT                NULL,
    max_users      INT                NULL,
    geofence_included BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order     INT            NOT NULL DEFAULT 0,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    CONSTRAINT pk_plans PRIMARY KEY (id),
    CONSTRAINT uk_plans_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_plans_active ON plans (is_active, sort_order);

-- One row per company: the subscription it is on right now. Plan changes update
-- this row; the billing history lives in `invoices` and the audit log.
CREATE TABLE subscriptions (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    company_id     BIGINT      NOT NULL,
    plan_id        BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date     DATE        NOT NULL,
    current_period_start DATE  NOT NULL,
    current_period_end   DATE  NOT NULL,
    trial_ends_at  DATE            NULL,
    cancelled_at   DATETIME(6)     NULL,
    auto_renew     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    CONSTRAINT pk_subscriptions PRIMARY KEY (id),
    CONSTRAINT uk_subscriptions_company UNIQUE (company_id),
    CONSTRAINT fk_subscriptions_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_subscriptions_plan    FOREIGN KEY (plan_id)    REFERENCES plans (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_subscriptions_status      ON subscriptions (status);
CREATE INDEX idx_subscriptions_period_end  ON subscriptions (current_period_end);
CREATE INDEX idx_subscriptions_plan        ON subscriptions (plan_id);

-- Amounts are frozen at issue time: changing a plan's price must never rewrite
-- an invoice that was already sent.
CREATE TABLE invoices (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    company_id      BIGINT         NOT NULL,
    subscription_id BIGINT             NULL,
    invoice_number  VARCHAR(50)    NOT NULL,
    plan_code       VARCHAR(50)    NOT NULL,
    plan_name       VARCHAR(100)   NOT NULL,
    period_start    DATE           NOT NULL,
    period_end      DATE           NOT NULL,
    amount          DECIMAL(12, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'IDR',
    status          VARCHAR(20)    NOT NULL DEFAULT 'ISSUED',
    issued_at       DATETIME(6)    NOT NULL,
    due_date        DATE           NOT NULL,
    paid_at         DATETIME(6)        NULL,
    payment_reference VARCHAR(100)     NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoices_subscription_period UNIQUE (subscription_id, period_start),
    CONSTRAINT fk_invoices_company      FOREIGN KEY (company_id)      REFERENCES companies (id),
    CONSTRAINT fk_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_invoices_company_status ON invoices (company_id, status);
CREATE INDEX idx_invoices_due_date       ON invoices (status, due_date);

-- ---------------------------------------------------------------------
-- Catalogue seed. FREE is the plan every self-service sign-up lands on.
-- ---------------------------------------------------------------------
INSERT INTO plans (code, name, description, price_amount, currency, billing_period,
                   max_employees, max_locations, max_users, geofence_included,
                   is_active, sort_order, created_at, updated_at)
VALUES
    ('FREE', 'Free', 'Untuk tim kecil yang baru mulai',
     0.00, 'IDR', 'MONTHLY', 10, 1, 15, FALSE, TRUE, 1,
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('BASIC', 'Basic', 'Absensi lengkap dengan validasi lokasi',
     299000.00, 'IDR', 'MONTHLY', 50, 3, 75, TRUE, TRUE, 2,
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('PRO', 'Pro', 'Untuk perusahaan dengan banyak cabang',
     999000.00, 'IDR', 'MONTHLY', 250, 10, 400, TRUE, TRUE, 3,
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    ('ENTERPRISE', 'Enterprise', 'Tanpa batas karyawan, lokasi, dan pengguna',
     4999000.00, 'IDR', 'MONTHLY', NULL, NULL, NULL, TRUE, TRUE, 4,
     CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- Companies that existed before billing are placed on FREE so no tenant is
-- left without a subscription and every limit check has something to read.
INSERT INTO subscriptions (company_id, plan_id, status, start_date,
                           current_period_start, current_period_end,
                           auto_renew, created_at, updated_at)
SELECT c.id,
       (SELECT p.id FROM plans p WHERE p.code = 'FREE'),
       'ACTIVE',
       CAST(c.created_at AS DATE),
       CAST(c.created_at AS DATE),
       CAST(TIMESTAMPADD(MONTH, 1, c.created_at) AS DATE),
       TRUE,
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
FROM companies c;
