# Schemas & Flow Diagrams

## Entity-Relationship Diagram

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

## Invoice Lifecycle

```mermaid
stateDiagram-v2
    [*] --> DRAFT : invoice created
    DRAFT --> FINALIZED : billing cycle closes period
    FINALIZED --> PAID : payment succeeds
    FINALIZED --> VOID : manually voided
```

## Subscription Lifecycle

```mermaid
stateDiagram-v2
    [*] --> ACTIVE : tenant subscribes
    ACTIVE --> PAST_DUE : payment fails at billing cycle
    PAST_DUE --> ACTIVE : payment retried and succeeds
    ACTIVE --> CANCELLED : tenant cancels
    PAST_DUE --> CANCELLED : tenant cancels
```

## Payment Flow

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

## Billing Cycle Flow

Runs nightly via `@Scheduled`. Each subscription is processed in its own isolated transaction — a failure for one tenant does not affect others.

```mermaid
flowchart TD
    A[BillingCycleService - scheduled daily at midnight] --> B[ShedLock acquires distributed lock]
    B --> C[Load all ACTIVE subscriptions\nwhere current_period_end = today]
    C --> D[For each subscription\nrun processTenant in own tx]
    D --> E[Aggregate usage records\nfor the billing period]
    E --> F[Build invoice\nbase fee + overage line items]
    F --> G[Finalize invoice\nstatus = FINALIZED]
    G --> H[Attempt charge\nvia payment gateway]
    H --> I{Gateway result}
    I -- SUCCESS --> J[Mark invoice PAID\nRoll period forward 30 days]
    I -- FAILURE --> K[Mark payment FAILED\nIf 3+ failures: set PAST_DUE]
```
