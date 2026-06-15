package com.ecren.billing.web;

import com.ecren.billing.BaseIT;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.dto.request.ReportUsageRequest;
import com.ecren.billing.dto.response.UsageRecordResponse;
import com.ecren.billing.dto.response.UsageSummaryResponse;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import com.ecren.billing.repository.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsageReportingIT extends BaseIT {

    @Autowired
    UsageRecordRepository usageRecordRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    JdbcTemplate jdbc;

    private Tenant tenant;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM invoice_line_items");
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM usage_records");
        jdbc.execute("DELETE FROM ledger_entries");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("DELETE FROM plan_metric_limits");
        jdbc.execute("DELETE FROM plans");
        jdbc.execute("DELETE FROM tenants");

        tenant = new Tenant();
        tenant.setName("Usage Corp");
        tenant.setEmail("usage@example.com");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        Plan plan = new Plan();
        plan.setName("Basic");
        plan.setSlug("basic-usage");
        plan.setBasePriceCents(999L);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(PlanStatus.ACTIVE);

        PlanMetricLimit apiLimit = new PlanMetricLimit();
        apiLimit.setPlan(plan);
        apiLimit.setMetric(UsageMetric.API_CALLS);
        apiLimit.setIncludedQuantity(100L);
        apiLimit.setOveragePricePerUnitCents(1L);

        PlanMetricLimit storageLimit = new PlanMetricLimit();
        storageLimit.setPlan(plan);
        storageLimit.setMetric(UsageMetric.STORAGE_GB);
        storageLimit.setIncludedQuantity(10L);
        storageLimit.setOveragePricePerUnitCents(5L);

        plan.setMetricLimits(List.of(apiLimit, storageLimit));
        plan = planRepository.save(plan);

        subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlanId(plan.getId());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(LocalDate.now().minusDays(1));
        subscription.setCurrentPeriodEnd(LocalDate.now().plusDays(29));
        subscription = subscriptionRepository.save(subscription);
    }

    @Test
    void reportUsage_givenValidRequest_thenReturns201() {
        ResponseEntity<UsageRecordResponse> response = rest.exchange(
                "/api/v1/usage", HttpMethod.POST,
                new HttpEntity<>(new ReportUsageRequest(UsageMetric.API_CALLS, 10L, "key-001"), userHeaders(tenant.getId())),
                UsageRecordResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UsageRecordResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.usageRecordId()).isNotNull();
        assertThat(body.metric()).isEqualTo("API_CALLS");
        assertThat(body.quantity()).isEqualTo(10L);
        assertThat(body.idempotencyKey()).isEqualTo("key-001");
        assertThat(body.tenantId()).isEqualTo(tenant.getId());
    }

    @Test
    void reportUsage_givenDuplicateIdempotencyKey_thenReturns200WithSameRecord() {
        HttpHeaders headers = userHeaders(tenant.getId());
        var request = new ReportUsageRequest(UsageMetric.API_CALLS, 10L, "key-dup");

        ResponseEntity<UsageRecordResponse> first = rest.exchange(
                "/api/v1/usage", HttpMethod.POST, new HttpEntity<>(request, headers), UsageRecordResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<UsageRecordResponse> second = rest.exchange(
                "/api/v1/usage", HttpMethod.POST, new HttpEntity<>(request, headers), UsageRecordResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().usageRecordId()).isEqualTo(first.getBody().usageRecordId());
    }

    @Test
    void getUsageSummary_givenUsageReported_thenShowsConsumedAndOverage() {
        HttpHeaders headers = userHeaders(tenant.getId());
        rest.exchange("/api/v1/usage", HttpMethod.POST,
                new HttpEntity<>(new ReportUsageRequest(UsageMetric.API_CALLS, 150L, "key-summary-1"), headers),
                UsageRecordResponse.class);

        ResponseEntity<UsageSummaryResponse> response = rest.exchange(
                "/api/v1/usage/summary", HttpMethod.GET,
                new HttpEntity<>(headers), UsageSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UsageSummaryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.metrics()).isNotEmpty();

        UsageSummaryResponse.MetricSummary apiSummary = body.metrics().stream()
                .filter(m -> m.metric().equals("API_CALLS"))
                .findFirst().orElseThrow();
        assertThat(apiSummary.consumed()).isEqualTo(150L);
        assertThat(apiSummary.included()).isEqualTo(100L);
        assertThat(apiSummary.overage()).isEqualTo(50L);
    }

    @Test
    void getUsageSummary_givenNoUsage_thenShowsZeroConsumed() {
        ResponseEntity<UsageSummaryResponse> response = rest.exchange(
                "/api/v1/usage/summary", HttpMethod.GET,
                new HttpEntity<>(userHeaders(tenant.getId())), UsageSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        UsageSummaryResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.metrics()).hasSize(2);
        body.metrics().forEach(m -> {
            assertThat(m.consumed()).isZero();
            assertThat(m.overage()).isZero();
        });
    }

    @Test
    void reportUsage_givenNoActiveSubscription_thenReturns404() {
        subscriptionRepository.deleteAll();

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/usage", HttpMethod.POST,
                new HttpEntity<>(new ReportUsageRequest(UsageMetric.API_CALLS, 5L, "key-nosub"), userHeaders(tenant.getId())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
