package com.ecren.billing.web;

import com.ecren.billing.TestcontainersConfiguration;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.LedgerEntry;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import com.ecren.billing.repository.UsageRecordRepository;
import com.ecren.billing.service.BillingCycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BillingCycleIT {

    @Autowired
    BillingCycleService billingCycleService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    UsageRecordRepository usageRecordRepository;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM invoice_line_items");
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM ledger_entries");
        jdbc.execute("DELETE FROM usage_records");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("DELETE FROM plan_metric_limits");
        jdbc.execute("DELETE FROM plans");
        jdbc.execute("DELETE FROM tenants");
    }

    @Test
    void runBillingCycle_givenActiveSubscriptionWithUsage_thenInvoiceCreatedAndPeriodRolled() {
        LocalDate today = LocalDate.now();

        Tenant tenant = new Tenant();
        tenant.setName("Billing Corp");
        tenant.setEmail("billing@example.com");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        Plan plan = new Plan();
        plan.setName("Pro");
        plan.setSlug("pro-billing-cycle");
        plan.setBasePriceCents(2000L);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(PlanStatus.ACTIVE);

        PlanMetricLimit apiLimit = new PlanMetricLimit();
        apiLimit.setPlan(plan);
        apiLimit.setMetric(UsageMetric.API_CALLS);
        apiLimit.setIncludedQuantity(100L);
        apiLimit.setOveragePricePerUnitCents(10L);
        plan.setMetricLimits(List.of(apiLimit));
        plan = planRepository.save(plan);

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(today.minusDays(30));
        subscription.setCurrentPeriodEnd(today);
        subscription = subscriptionRepository.save(subscription);

        // Report usage above the included limit (150 API calls, 50 overage)
        jdbc.update(
            "INSERT INTO usage_records (id, tenant_id, subscription_id, metric, quantity, recorded_at, idempotency_key) VALUES (gen_random_uuid(), ?, ?, 'API_CALLS', 150, now(), ?)",
            tenant.getId(), subscription.getId(), "billing-cycle-it-usage-1"
        );

        billingCycleService.runBillingCycle();

        // Invoice should exist and be PAID
        List<Invoice> invoices = invoiceRepository.findAll();
        assertThat(invoices).hasSize(1);
        Invoice invoice = invoices.get(0);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getTenantId()).isEqualTo(tenant.getId());
        // BASE_FEE (2000) + USAGE_OVERAGE (50 * 10 = 500) = 2500
        assertThat(invoice.getTotalCents()).isEqualTo(2500L);

        // Ledger should have exactly 2 entries (CHARGE + PAYMENT)
        List<LedgerEntry> ledgerEntries = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId());
        assertThat(ledgerEntries).hasSize(2);

        // Period rolled forward
        Subscription updated = subscriptionRepository.findById(subscription.getId()).orElseThrow();
        assertThat(updated.getCurrentPeriodStart()).isEqualTo(today);
        assertThat(updated.getCurrentPeriodEnd()).isEqualTo(today.plusDays(30));
    }
}
