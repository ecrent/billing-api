-- V1: Full schema. No seed data — plans are seeded by DataInitializer (@Profile("dev")).
-- Flyway runs this once on a fresh DB, then checksums it. Never edit after first deployment.

CREATE TABLE tenants (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE plans (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(100) NOT NULL,
    slug              VARCHAR(100) NOT NULL UNIQUE,
    base_price_cents  BIGINT       NOT NULL,
    billing_interval  VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE plan_metric_limits (
    id                           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                      UUID        NOT NULL REFERENCES plans(id),
    metric                       VARCHAR(50) NOT NULL,
    included_quantity             BIGINT      NOT NULL,
    overage_price_per_unit_cents  BIGINT      NOT NULL
);

CREATE TABLE subscriptions (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL REFERENCES tenants(id),
    plan_id              UUID        NOT NULL REFERENCES plans(id),
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_period_start DATE        NOT NULL,
    current_period_end   DATE        NOT NULL,
    cancelled_at         TIMESTAMP,
    version              BIGINT      NOT NULL DEFAULT 0,
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now()
);

-- Partial unique index: only one ACTIVE subscription per tenant.
-- A plain UNIQUE on tenant_id would block re-subscribing after cancellation.
-- This allows unlimited CANCELLED rows per tenant while enforcing the business rule.
CREATE UNIQUE INDEX subscriptions_one_active_per_tenant
    ON subscriptions (tenant_id)
    WHERE status = 'ACTIVE';

CREATE TABLE usage_records (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL REFERENCES tenants(id),
    subscription_id UUID         NOT NULL REFERENCES subscriptions(id),
    metric          VARCHAR(50)  NOT NULL,
    quantity        BIGINT       NOT NULL,
    recorded_at     TIMESTAMP    NOT NULL DEFAULT now(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE invoices (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    subscription_id UUID        NOT NULL REFERENCES subscriptions(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    period_start    DATE        NOT NULL,
    period_end      DATE        NOT NULL,
    total_cents     BIGINT      NOT NULL DEFAULT 0,
    due_date        DATE,
    finalized_at    TIMESTAMP,
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE invoice_line_items (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID         NOT NULL REFERENCES invoices(id),
    type             VARCHAR(30)  NOT NULL,
    description      VARCHAR(500) NOT NULL,
    quantity         BIGINT       NOT NULL DEFAULT 1,
    unit_price_cents BIGINT       NOT NULL,
    amount_cents     BIGINT       NOT NULL
);

-- Append-only: rows are never updated or deleted.
-- Balance = SUM(amount_cents) — no cached balance column.
CREATE TABLE ledger_entries (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id),
    type         VARCHAR(20)  NOT NULL,
    amount_cents BIGINT       NOT NULL,
    description  VARCHAR(500),
    reference_id UUID,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID         NOT NULL REFERENCES tenants(id),
    invoice_id        UUID         NOT NULL REFERENCES invoices(id),
    amount_cents      BIGINT       NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    idempotency_key   VARCHAR(255) NOT NULL UNIQUE,
    gateway_reference VARCHAR(255),
    created_at        TIMESTAMP    NOT NULL DEFAULT now()
);

-- ShedLock uses this table to coordinate distributed job locking.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
