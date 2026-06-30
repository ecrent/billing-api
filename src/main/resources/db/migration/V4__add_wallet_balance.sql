-- Demo wallet: every tenant starts with $1,000 of play money. Recurring charges
-- draw down this balance; once it can't cover a charge the subscription drops.
ALTER TABLE tenants
    ADD COLUMN wallet_balance_cents BIGINT NOT NULL DEFAULT 100000;
