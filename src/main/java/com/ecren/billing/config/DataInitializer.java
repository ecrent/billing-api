package com.ecren.billing.config;

import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.repository.PlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@Profile("dev")
public class DataInitializer implements ApplicationRunner {

    static final UUID ALICE_TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID BOB_TENANT_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID CAROL_TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final UUID ALICE_SUB_ID     = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final UUID BOB_SUB_ID       = UUID.fromString("00000000-0000-0000-0000-000000000022");
    static final UUID CAROL_SUB_ID     = UUID.fromString("00000000-0000-0000-0000-000000000033");

    static final UUID ALICE_INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    static final UUID BOB_INVOICE_ID   = UUID.fromString("00000000-0000-0000-0000-000000000200");
    static final UUID CAROL_INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000300");

    static final UUID ALICE_PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    static final UUID BOB_PAYMENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000002000");
    static final UUID CAROL_PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000003000");

    private final PlanRepository planRepository;

    @PersistenceContext
    private EntityManager em;

    public DataInitializer(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            return;
        }

        // --- Plans (no pre-assigned IDs — auto-generated) ---
        Plan basic = plan("Basic", "basic", 900L,
                limit(UsageMetric.API_CALLS, 10_000L, 1L),
                limit(UsageMetric.STORAGE_GB, 5L, 50L));

        Plan pro = plan("Pro", "pro", 2900L,
                limit(UsageMetric.API_CALLS, 100_000L, 1L),
                limit(UsageMetric.STORAGE_GB, 50L, 30L));

        Plan enterprise = plan("Enterprise", "enterprise", 9900L,
                limit(UsageMetric.API_CALLS, 1_000_000L, 1L),
                limit(UsageMetric.STORAGE_GB, 500L, 20L));

        planRepository.saveAll(List.of(basic, pro, enterprise));
        em.flush();

        // Re-read plan IDs (auto-generated)
        UUID basicId = planRepository.findAll().stream()
                .filter(p -> "basic".equals(p.getSlug())).findFirst().orElseThrow().getId();
        UUID proId = planRepository.findAll().stream()
                .filter(p -> "pro".equals(p.getSlug())).findFirst().orElseThrow().getId();
        UUID enterpriseId = planRepository.findAll().stream()
                .filter(p -> "enterprise".equals(p.getSlug())).findFirst().orElseThrow().getId();

        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
        LocalDateTime now = LocalDateTime.now();

        seedTenant(ALICE_TENANT_ID, "Alice Johnson", "alice@example.com",
                ALICE_SUB_ID, basicId, ALICE_INVOICE_ID, 900L, ALICE_PAYMENT_ID,
                periodStart, periodEnd, now);

        seedTenant(BOB_TENANT_ID, "Bob Smith", "bob@example.com",
                BOB_SUB_ID, proId, BOB_INVOICE_ID, 2900L, BOB_PAYMENT_ID,
                periodStart, periodEnd, now);

        seedTenant(CAROL_TENANT_ID, "Carol Davis", "carol@example.com",
                CAROL_SUB_ID, enterpriseId, CAROL_INVOICE_ID, 9900L, CAROL_PAYMENT_ID,
                periodStart, periodEnd, now);
    }

    private void seedTenant(
            UUID tenantId, String name, String email,
            UUID subId, UUID planId,
            UUID invoiceId, long amountCents, UUID paymentId,
            LocalDate periodStart, LocalDate periodEnd, LocalDateTime now) {

        // Use native SQL to INSERT with pre-assigned UUIDs, bypassing JPA isNew() / merge() confusion
        em.createNativeQuery(
                "INSERT INTO tenants (id, name, email, status, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, 'ACTIVE', ?4, ?4)")
                .setParameter(1, tenantId)
                .setParameter(2, name)
                .setParameter(3, email)
                .setParameter(4, now)
                .executeUpdate();

        em.createNativeQuery(
                "INSERT INTO subscriptions (id, tenant_id, plan_id, status, current_period_start, current_period_end, version, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, 'ACTIVE', ?4, ?5, 0, ?6, ?6)")
                .setParameter(1, subId)
                .setParameter(2, tenantId)
                .setParameter(3, planId)
                .setParameter(4, periodStart)
                .setParameter(5, periodEnd)
                .setParameter(6, now)
                .executeUpdate();

        em.createNativeQuery(
                "INSERT INTO invoices (id, tenant_id, subscription_id, status, period_start, period_end, total_cents, due_date, finalized_at, paid_at, created_at, updated_at) " +
                "VALUES (?1, ?2, ?3, 'PAID', ?4, ?5, ?6, ?5, ?7, ?7, ?7, ?7)")
                .setParameter(1, invoiceId)
                .setParameter(2, tenantId)
                .setParameter(3, subId)
                .setParameter(4, periodStart)
                .setParameter(5, periodEnd)
                .setParameter(6, amountCents)
                .setParameter(7, now)
                .executeUpdate();

        UUID lineItemId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO invoice_line_items (id, invoice_id, type, description, quantity, unit_price_cents, amount_cents) " +
                "VALUES (?1, ?2, 'BASE_FEE', 'Base fee', 1, ?3, ?3)")
                .setParameter(1, lineItemId)
                .setParameter(2, invoiceId)
                .setParameter(3, amountCents)
                .executeUpdate();

        em.createNativeQuery(
                "INSERT INTO payments (id, tenant_id, invoice_id, amount_cents, status, idempotency_key, gateway_reference, created_at) " +
                "VALUES (?1, ?2, ?3, ?4, 'SUCCEEDED', ?5, ?6, ?7)")
                .setParameter(1, paymentId)
                .setParameter(2, tenantId)
                .setParameter(3, invoiceId)
                .setParameter(4, amountCents)
                .setParameter(5, "seed-" + tenantId)
                .setParameter(6, "gw-seed-" + tenantId)
                .setParameter(7, now)
                .executeUpdate();

        UUID chargeId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO ledger_entries (id, tenant_id, type, amount_cents, description, reference_id, created_at) " +
                "VALUES (?1, ?2, 'CHARGE', ?3, 'Invoice charge', ?4, ?5)")
                .setParameter(1, chargeId)
                .setParameter(2, tenantId)
                .setParameter(3, amountCents)
                .setParameter(4, invoiceId)
                .setParameter(5, now)
                .executeUpdate();

        UUID paymentEntryId = UUID.randomUUID();
        em.createNativeQuery(
                "INSERT INTO ledger_entries (id, tenant_id, type, amount_cents, description, reference_id, created_at) " +
                "VALUES (?1, ?2, 'PAYMENT', ?3, 'Payment received', ?4, ?5)")
                .setParameter(1, paymentEntryId)
                .setParameter(2, tenantId)
                .setParameter(3, -amountCents)
                .setParameter(4, paymentId)
                .setParameter(5, now)
                .executeUpdate();
    }

    private Plan plan(String name, String slug, long basePriceCents, PlanMetricLimit... limits) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setSlug(slug);
        plan.setBasePriceCents(basePriceCents);
        for (PlanMetricLimit limit : limits) {
            limit.setPlan(plan);
        }
        plan.setMetricLimits(List.of(limits));
        return plan;
    }

    private PlanMetricLimit limit(UsageMetric metric, long included, long overageCents) {
        PlanMetricLimit l = new PlanMetricLimit();
        l.setMetric(metric);
        l.setIncludedQuantity(included);
        l.setOveragePricePerUnitCents(overageCents);
        return l;
    }
}
