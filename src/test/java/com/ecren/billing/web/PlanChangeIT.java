package com.ecren.billing.web;

import com.ecren.billing.BaseIT;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.dto.request.ChangePlanRequest;
import com.ecren.billing.dto.response.ChangePlanResponse;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanChangeIT extends BaseIT {

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.execute("DELETE FROM invoice_line_items");
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM ledger_entries");
        jdbc.execute("DELETE FROM usage_records");
        subscriptionRepository.deleteAll();
        jdbc.execute("DELETE FROM plan_metric_limits");
        planRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void changePlan_givenUpgrade_thenReturns200WithPaidInvoiceAndLineItems() {
        Tenant tenant = createTenant("Plan Change Corp", "planchange@example.com");
        Plan basicPlan = createPlan("Basic", "basic-change", 3000L);
        Plan proPlan = createPlan("Pro", "pro-change", 6000L);
        createSubscription(tenant.getId(), basicPlan.getId());

        ResponseEntity<ChangePlanResponse> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(new ChangePlanRequest(proPlan.getId()), userHeaders(tenant.getId())),
                ChangePlanResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ChangePlanResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.invoice()).isNotNull();
        assertThat(body.invoice().status()).isEqualTo("PAID");
        assertThat(body.invoice().lineItems()).hasSize(2);
        assertThat(body.invoice().lineItems()).anyMatch(li -> li.type().equals("PRORATION_CREDIT"));
        assertThat(body.invoice().lineItems()).anyMatch(li -> li.type().equals("PRORATION_CHARGE"));

        Subscription updated = subscriptionRepository.findByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.ACTIVE)
                .orElseThrow();
        assertThat(updated.getPlanId()).isEqualTo(proPlan.getId());

        long ledgerCount = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).size();
        assertThat(ledgerCount).isEqualTo(2);
    }

    @Test
    void changePlan_givenUpgradePaymentFailure_thenReturns402AndSubscriptionIsPastDue() {
        Tenant tenant = createTenant("Fail Pay Corp", "failpay@example.com");
        Plan basicPlan = createPlan("Basic", "basic-fail", 3000L);
        Plan proPlan = createPlan("Pro", "pro-fail", 6000L);
        createSubscription(tenant.getId(), basicPlan.getId());

        HttpHeaders headers = userHeaders(tenant.getId());
        headers.set("X-Mock-Gateway-Result", "FAIL");

        ResponseEntity<ChangePlanResponse> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(new ChangePlanRequest(proPlan.getId()), headers),
                ChangePlanResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(402);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().invoice().status()).isEqualTo("FINALIZED");
        assertThat(subscriptionRepository.existsByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.PAST_DUE)).isTrue();
    }

    @Test
    void changePlan_givenDowngrade_thenReturns200WithoutInvoiceAndAppliesNextPeriod() {
        Tenant tenant = createTenant("Downgrade Corp", "downgrade@example.com");
        Plan proPlan = createPlan("Pro", "pro-downgrade", 6000L);
        Plan basicPlan = createPlan("Basic", "basic-downgrade", 3000L);
        createSubscription(tenant.getId(), proPlan.getId());

        ResponseEntity<ChangePlanResponse> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(new ChangePlanRequest(basicPlan.getId()), userHeaders(tenant.getId())),
                ChangePlanResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ChangePlanResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.invoice()).isNull();
        assertThat(body.message()).contains("Basic");

        Subscription updated = subscriptionRepository.findByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.ACTIVE)
                .orElseThrow();
        assertThat(updated.getPlanId()).isEqualTo(proPlan.getId()); // unchanged until next period
        assertThat(updated.getPendingPlanId()).isEqualTo(basicPlan.getId());

        long ledgerCount = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).size();
        assertThat(ledgerCount).isEqualTo(0);
        assertThat(invoiceRepository.findByTenantId(tenant.getId(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isEqualTo(0);
    }

    @Test
    void changePlan_givenSamePlan_thenReturns409() {
        Tenant tenant = createTenant("Same Plan Corp", "sameplan@example.com");
        Plan basicPlan = createPlan("Basic", "basic-same", 3000L);
        createSubscription(tenant.getId(), basicPlan.getId());

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(new ChangePlanRequest(basicPlan.getId()), userHeaders(tenant.getId())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    private Tenant createTenant(String name, String email) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setEmail(email);
        tenant.setStatus(TenantStatus.ACTIVE);
        return tenantRepository.save(tenant);
    }

    private Plan createPlan(String name, String slug, long basePriceCents) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setSlug(slug);
        plan.setBasePriceCents(basePriceCents);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(PlanStatus.ACTIVE);
        return planRepository.save(plan);
    }

    private Subscription createSubscription(UUID tenantId, UUID planId) {
        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(LocalDate.now());
        subscription.setCurrentPeriodEnd(LocalDate.now().plusDays(29));
        return subscriptionRepository.save(subscription);
    }
}
