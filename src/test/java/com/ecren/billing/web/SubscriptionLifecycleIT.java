package com.ecren.billing.web;

import com.ecren.billing.TestcontainersConfiguration;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.dto.request.CreateSubscriptionRequest;
import com.ecren.billing.dto.response.SubscriptionResponse;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
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
class SubscriptionLifecycleIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM invoice_line_items");
        jdbc.execute("DELETE FROM invoices");
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void subscribe_givenValidPlan_thenReturns201WithLocation() {
        Tenant tenant = createTenant("Sub Corp", "subcorp@example.com");
        Plan plan = createPlan("Basic", "basic-sub");

        var request = new CreateSubscriptionRequest(plan.getId());
        HttpHeaders headers = headersWithTenantId(tenant.getId());

        ResponseEntity<SubscriptionResponse> response = rest.exchange(
                "/api/v1/subscriptions", HttpMethod.POST,
                new HttpEntity<>(request, headers), SubscriptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();

        SubscriptionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.subscriptionId()).isNotNull();
        assertThat(body.tenantId()).isEqualTo(tenant.getId());
        assertThat(body.planId()).isEqualTo(plan.getId());
        assertThat(body.status()).isEqualTo("ACTIVE");
        assertThat(body.currentPeriodStart()).isEqualTo(LocalDate.now());
        assertThat(body.currentPeriodEnd()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(body.createdAt()).isNotNull();
        assertThat(body.updatedAt()).isNotNull();
    }

    @Test
    void getCurrent_givenActiveSubscription_thenReturns200() {
        Tenant tenant = createTenant("Get Sub Corp", "getsubcorp@example.com");
        Plan plan = createPlan("Pro", "pro-get");

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        rest.exchange("/api/v1/subscriptions", HttpMethod.POST,
                new HttpEntity<>(new CreateSubscriptionRequest(plan.getId()), headers),
                SubscriptionResponse.class);

        ResponseEntity<SubscriptionResponse> response = rest.exchange(
                "/api/v1/subscriptions/current", HttpMethod.GET,
                new HttpEntity<>(headers), SubscriptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SubscriptionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("ACTIVE");
    }

    @Test
    void cancel_givenActiveSubscription_thenReturns200WithCancelledAtSet() {
        Tenant tenant = createTenant("Cancel Corp", "cancelcorp@example.com");
        Plan plan = createPlan("Enterprise", "enterprise-cancel");

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        rest.exchange("/api/v1/subscriptions", HttpMethod.POST,
                new HttpEntity<>(new CreateSubscriptionRequest(plan.getId()), headers),
                SubscriptionResponse.class);

        ResponseEntity<SubscriptionResponse> response = rest.exchange(
                "/api/v1/subscriptions/current", HttpMethod.DELETE,
                new HttpEntity<>(headers), SubscriptionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SubscriptionResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("CANCELLED");
        assertThat(body.cancelledAt()).isNotNull();
    }

    @Test
    void subscribe_givenAlreadyActiveSubscription_thenReturns409() {
        Tenant tenant = createTenant("Dup Sub Corp", "dupsubcorp@example.com");
        Plan plan = createPlan("Starter", "starter-dup");

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        rest.exchange("/api/v1/subscriptions", HttpMethod.POST,
                new HttpEntity<>(new CreateSubscriptionRequest(plan.getId()), headers),
                SubscriptionResponse.class);

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/subscriptions", HttpMethod.POST,
                new HttpEntity<>(new CreateSubscriptionRequest(plan.getId()), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void subscribe_givenUnknownPlan_thenReturns404() {
        Tenant tenant = createTenant("No Plan Corp", "noplancorp@example.com");

        HttpHeaders headers = headersWithTenantId(tenant.getId());
        var request = new CreateSubscriptionRequest(UUID.randomUUID());

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/subscriptions", HttpMethod.POST,
                new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

    private Plan createPlan(String name, String slug) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setSlug(slug);
        plan.setBasePriceCents(999L);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(PlanStatus.ACTIVE);
        return planRepository.save(plan);
    }
}
