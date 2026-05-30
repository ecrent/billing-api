package com.ecren.billing.web;

import com.ecren.billing.TestcontainersConfiguration;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.dto.request.ChangePlanRequest;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PlanChangeIT {

    @Autowired
    TestRestTemplate rest;

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

    @AfterEach
    void tearDown() {
        jdbc.execute("DELETE FROM invoice_line_items");
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM ledger_entries");
        jdbc.execute("DELETE FROM usage_records");
        subscriptionRepository.deleteAll();
        jdbc.execute("DELETE FROM plan_metric_limits");
        planRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @BeforeEach
    void setUp() {
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
    void changePlan_givenSuccess_thenReturns200WithPaidInvoiceAndLineItems() {
        Tenant tenant = createTenant("Plan Change Corp", "planchange@example.com");
        Plan basicPlan = createPlan("Basic", "basic-change", 3000L);
        Plan proPlan = createPlan("Pro", "pro-change", 6000L);
        Subscription subscription = createSubscription(tenant.getId(), basicPlan.getId());

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        var request = new ChangePlanRequest(proPlan.getId());

        ResponseEntity<InvoiceResponse> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        InvoiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("PAID");
        assertThat(body.lineItems()).hasSize(2);

        boolean hasCredit = body.lineItems().stream()
                .anyMatch(li -> li.type().equals("PRORATION_CREDIT"));
        boolean hasCharge = body.lineItems().stream()
                .anyMatch(li -> li.type().equals("PRORATION_CHARGE"));
        assertThat(hasCredit).isTrue();
        assertThat(hasCharge).isTrue();

        Subscription updated = subscriptionRepository.findByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.ACTIVE)
                .orElseThrow();
        assertThat(updated.getPlanId()).isEqualTo(proPlan.getId());

        long ledgerCount = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).size();
        assertThat(ledgerCount).isEqualTo(2);
    }

    @Test
    void changePlan_givenPaymentFailure_thenReturns402AndSubscriptionIsPastDue() {
        Tenant tenant = createTenant("Fail Pay Corp", "failpay@example.com");
        Plan basicPlan = createPlan("Basic", "basic-fail", 3000L);
        Plan proPlan = createPlan("Pro", "pro-fail", 6000L);
        createSubscription(tenant.getId(), basicPlan.getId());

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        headers.set("X-Mock-Gateway-Result", "FAIL");
        var request = new ChangePlanRequest(proPlan.getId());

        ResponseEntity<InvoiceResponse> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                InvoiceResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(402);

        InvoiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("FINALIZED");

        boolean isPastDue = subscriptionRepository.existsByTenantIdAndStatus(tenant.getId(), SubscriptionStatus.PAST_DUE);
        assertThat(isPastDue).isTrue();
    }

    @Test
    void changePlan_givenSamePlan_thenReturns409() {
        Tenant tenant = createTenant("Same Plan Corp", "sameplan@example.com");
        Plan basicPlan = createPlan("Basic", "basic-same", 3000L);
        createSubscription(tenant.getId(), basicPlan.getId());

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        var request = new ChangePlanRequest(basicPlan.getId());

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/subscriptions/current/change-plan",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    private HttpHeaders headersWithTenantId(UUID tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        return headers;
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
