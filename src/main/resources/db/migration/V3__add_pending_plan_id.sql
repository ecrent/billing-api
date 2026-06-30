-- Downgrades are deferred to the next billing cycle instead of applying immediately.
-- The target plan is parked here until BillingCycleService rolls the period forward.
ALTER TABLE subscriptions
    ADD COLUMN pending_plan_id UUID REFERENCES plans(id);
