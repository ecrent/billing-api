# Billing API

A multi-tenant SaaS subscription billing REST API built with Spring Boot 3.5 and Java 21.

![CI](https://github.com/ecrent/billing-api/actions/workflows/ci.yml/badge.svg)

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| ORM | Spring Data JPA + Hibernate |
| Mapping | MapStruct |
| Scheduled jobs | Spring @Scheduled + ShedLock |
| Docs | springdoc-openapi 2.x (OpenAPI 3.0) |
| Tests | JUnit 5 + Mockito + Testcontainers |
| Build | Maven |

## Domain Model

### Entity-Relationship Diagram

```mermaid
erDiagram
    tenants {
        UUID id PK
        VARCHAR name
        VARCHAR email
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    plans {
        UUID id PK
        VARCHAR name
        VARCHAR slug
        BIGINT base_price_cents
        VARCHAR billing_interval
        VARCHAR status
        TIMESTAMP created_at
    }
    plan_metric_limits {
        UUID id PK
        UUID plan_id FK
        VARCHAR metric
        BIGINT included_quantity
        BIGINT overage_price_per_unit_cents
    }
    subscriptions {
        UUID id PK
        UUID tenant_id FK
        UUID plan_id FK
        VARCHAR status
        DATE current_period_start
        DATE current_period_end
        TIMESTAMP cancelled_at
        BIGINT version
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    usage_records {
        UUID id PK
        UUID tenant_id FK
        UUID subscription_id FK
        VARCHAR metric
        BIGINT quantity
        TIMESTAMP recorded_at
        VARCHAR idempotency_key
    }
    invoices {
        UUID id PK
        UUID tenant_id FK
        UUID subscription_id FK
        VARCHAR status
        DATE period_start
        DATE period_end
        BIGINT total_cents
        DATE due_date
        TIMESTAMP finalized_at
        TIMESTAMP paid_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    invoice_line_items {
        UUID id PK
        UUID invoice_id FK
        VARCHAR type
        VARCHAR description
        BIGINT quantity
        BIGINT unit_price_cents
        BIGINT amount_cents
    }
    ledger_entries {
        UUID id PK
        UUID tenant_id FK
        VARCHAR type
        BIGINT amount_cents
        VARCHAR description
        UUID reference_id
        TIMESTAMP created_at
    }
    payments {
        UUID id PK
        UUID tenant_id FK
        UUID invoice_id FK
        BIGINT amount_cents
        VARCHAR status
        VARCHAR idempotency_key
        VARCHAR gateway_reference
        TIMESTAMP created_at
    }

    tenants ||--o{ subscriptions : "has"
    tenants ||--o{ usage_records : "generates"
    tenants ||--o{ invoices : "billed via"
    tenants ||--o{ ledger_entries : "accounted in"
    tenants ||--o{ payments : "pays via"
    plans ||--o{ subscriptions : "subscribed to"
    plans ||--o{ plan_metric_limits : "limits"
    subscriptions ||--o{ usage_records : "tracks"
    subscriptions ||--o{ invoices : "produces"
    invoices ||--o{ invoice_line_items : "itemised by"
    invoices ||--o{ payments : "settled by"
```

### Invoice Lifecycle

```mermaid
stateDiagram-v2
    [*] --> DRAFT : invoice created
    DRAFT --> FINALIZED : billing cycle closes period
    FINALIZED --> PAID : payment succeeds
    FINALIZED --> VOID : manually voided
```

### Subscription Lifecycle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : tenant subscribes
    ACTIVE --> PAST_DUE : payment fails at billing cycle
    PAST_DUE --> ACTIVE : payment retried and succeeds
    ACTIVE --> CANCELLED : tenant cancels
    PAST_DUE --> CANCELLED : tenant cancels
```

### Payment Flow

```mermaid
flowchart TD
    A[POST /payments] --> B{idempotency key\nalready exists?}
    B -- yes --> C[Return existing payment\n200 OK]
    B -- no --> D[Create PENDING payment]
    D --> E[Call MockPaymentGateway]
    E --> F{Gateway result}
    F -- SUCCESS --> G[Set status = SUCCEEDED\nMark invoice PAID\nAppend PAYMENT ledger entry]
    F -- FAILURE --> H[Set status = FAILED\nMark subscription PAST_DUE]
    G --> I[Return 201 Created]
    H --> J[Return 201 Created\nstatus = FAILED]
```

### Billing Cycle Flow

```mermaid
flowchart TD
    A[BillingCycleService\n@Scheduled daily] --> B[ShedLock acquires lock]
    B --> C[Find subscriptions where\ncurrent_period_end <= today\nstatus = ACTIVE or PAST_DUE]
    C --> D{For each subscription}
    D --> E[Aggregate usage records\nfor the period]
    E --> F[Build DRAFT invoice\nbase fee + overage line items]
    F --> G[Finalize invoice]
    G --> H[Attempt payment\nvia gateway]
    H --> I{Payment result}
    I -- SUCCESS --> J[Roll period forward\ncurrent_period_start = today\ncurrent_period_end = today + 1 month]
    I -- FAILURE --> K[Mark subscription PAST_DUE\nleave period unchanged]
    J --> D
    K --> D
```

## Key Design Decisions

**1. Append-only ledger**
Every financial event (payment, refund, proration credit) is a new row in `ledger_entries`. Balance is computed as `SUM(amount_cents)` — no cached column that can drift. This mirrors standard accounting systems: you never edit history, you only append. The audit trail is free.

**2. Idempotency pattern — pre-check, not try/catch**
Before inserting a payment or usage record, the service calls `findByIdempotencyKey`. If a row already exists, it returns it immediately. The alternative — catching `DataIntegrityViolationException` on the unique constraint — does not work with Spring's transaction management: the exception marks the current transaction as rollback-only, so no further writes can succeed in that transaction boundary.

**3. Proration math — integer arithmetic, biased toward the customer**
When a tenant changes plan mid-period, the credit for the old plan uses `Math.floorDiv(oldPrice × remainingDays, daysInPeriod)` (floor — favors the customer), and the charge for the new plan uses `Math.ceilDiv(newPrice × remainingDays, daysInPeriod)` (ceil — protects revenue). All amounts stay in integer cents with no floating-point rounding errors.

**4. ShedLock — distributed job locking over JDBC**
The daily billing job runs on every app instance. ShedLock ensures only one instance executes it at a time by acquiring a row-level lock in the `shedlock` table using the existing PostgreSQL connection. No external coordinator (Redis, Zookeeper) needed.

**5. Partial unique index on subscriptions**
```sql
CREATE UNIQUE INDEX subscriptions_one_active_per_tenant
    ON subscriptions (tenant_id)
    WHERE status = 'ACTIVE';
```
A plain `UNIQUE(tenant_id)` would block a tenant from resubscribing after cancellation. The `WHERE status = 'ACTIVE'` predicate allows unlimited historical `CANCELLED` rows while enforcing the one-active-subscription business rule at the database level.

**6. @Lazy self-proxy in BillingCycleService**
`processTenant()` is annotated `@Transactional`. Calling `this.processTenant()` bypasses Spring AOP entirely — the proxy is never in the call chain, so no transaction is created. The service injects itself via `@Lazy` so each per-tenant call goes through the proxy, giving every tenant its own isolated transaction. A failure for one tenant rolls back only that tenant's work.

## How to Run Locally

```bash
git clone https://github.com/ecrent/billing-api.git
cd billing-api
cp .env.example .env
docker compose up
```

Swagger UI: http://localhost:8080/swagger-ui.html

## How to Run Tests

```bash
./mvnw verify
```

Requires Docker — Testcontainers spins up PostgreSQL automatically.

## What's Intentionally Excluded

- **Real payment gateway** (Stripe/Braintree) — billing logic is the showcase
- **Authentication** — JWT arrives in the next portfolio project; this API uses `X-Tenant-ID` header
- **Email notifications**
- **Yearly billing intervals**
- **Refund endpoints** (ledger supports `REFUND` type structurally)
- **Plan creation via API** (plans seeded via Flyway)

## Live API

https://billing-api.ecren.dev/swagger-ui.html
