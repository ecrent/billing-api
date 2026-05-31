package com.ecren.billing.web;

import com.ecren.billing.TestcontainersConfiguration;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Sql(statements = "TRUNCATE TABLE plans CASCADE", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PlanCatalogIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    PlanRepository planRepository;

    @Test
    void listPlans_givenSeededPlans_thenReturnsAllActive() {
        Plan basic = buildPlan("Basic", "basic", 900L, PlanStatus.ACTIVE);
        Plan pro = buildPlan("Pro", "pro", 2900L, PlanStatus.ACTIVE);
        planRepository.saveAll(List.of(basic, pro));

        ResponseEntity<List<Map<String, Object>>> response = rest.exchange(
                "/api/v1/plans", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = response.getBody();
        assertThat(body).hasSize(2);
        assertThat(body).extracting(m -> m.get("name"))
                .containsExactlyInAnyOrder("Basic", "Pro");
    }

    @Test
    void getPlan_givenExistingId_thenReturns200WithMetricLimits() {
        Plan plan = buildPlan("Enterprise", "enterprise", 9900L, PlanStatus.ACTIVE);
        PlanMetricLimit limit = new PlanMetricLimit();
        limit.setPlan(plan);
        limit.setMetric(UsageMetric.API_CALLS);
        limit.setIncludedQuantity(1_000_000L);
        limit.setOveragePricePerUnitCents(1L);
        plan.setMetricLimits(List.of(limit));
        Plan saved = planRepository.save(plan);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/v1/plans/" + saved.getId(), HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("name")).isEqualTo("Enterprise");
        List<?> metricLimits = (List<?>) body.get("metricLimits");
        assertThat(metricLimits).hasSize(1);
        Map<?, ?> ml = (Map<?, ?>) metricLimits.get(0);
        assertThat(ml.get("metric")).isEqualTo("API_CALLS");
        assertThat(ml.get("includedQuantity")).isEqualTo(1_000_000);
    }

    @Test
    void getPlan_givenUnknownId_thenReturns404ProblemDetail() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/plans/" + UUID.randomUUID(), HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void listPlans_givenNoHeader_thenReturns200() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/plans", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Plan buildPlan(String name, String slug, long basePriceCents, PlanStatus status) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setSlug(slug);
        plan.setBasePriceCents(basePriceCents);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(status);
        plan.setMetricLimits(List.of());
        return plan;
    }
}
