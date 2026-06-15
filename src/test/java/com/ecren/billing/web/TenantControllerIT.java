package com.ecren.billing.web;

import com.ecren.billing.BaseIT;
import com.ecren.billing.dto.request.CreateTenantRequest;
import com.ecren.billing.dto.response.TenantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantControllerIT extends BaseIT {

    @Test
    void createTenant_givenAdminAndValidRequest_thenReturns201WithLocation() {
        var request = new CreateTenantRequest("Acme Corp", "acme@example.com");

        ResponseEntity<TenantResponse> response = rest.exchange(
                "/api/v1/tenants", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), TenantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        TenantResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tenantId()).isNotNull();
        assertThat(body.name()).isEqualTo("Acme Corp");
        assertThat(body.email()).isEqualTo("acme@example.com");
        assertThat(body.status()).isEqualTo("ACTIVE");
    }

    @Test
    void createTenant_givenDuplicateEmail_thenReturns400() {
        var request = new CreateTenantRequest("Dupe Corp", "dupe@example.com");
        rest.exchange("/api/v1/tenants", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), TenantResponse.class);

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getTenant_givenAdminAndExistingId_thenReturns200() {
        var created = rest.exchange(
                "/api/v1/tenants", HttpMethod.POST,
                new HttpEntity<>(new CreateTenantRequest("Get Corp", "getcorp@example.com"), adminHeaders()),
                TenantResponse.class);
        UUID id = created.getBody().tenantId();

        ResponseEntity<TenantResponse> response = rest.exchange(
                "/api/v1/tenants/" + id, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), TenantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tenantId()).isEqualTo(id);
    }

    @Test
    void getTenant_givenUnknownId_thenReturns404ProblemDetail() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants/" + UUID.randomUUID(), HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void tenantEndpoint_givenNoToken_thenReturns401() {
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants", HttpMethod.POST,
                new HttpEntity<>(new CreateTenantRequest("No Auth Corp", "noauth@example.com")),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tenantEndpoint_givenUserRole_thenReturns403() {
        // Tenant operations are ADMIN-only; a regular USER token should be forbidden
        UUID fakeTenantId = UUID.randomUUID();
        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants", HttpMethod.POST,
                new HttpEntity<>(new CreateTenantRequest("User Corp", "user@example.com"), userHeaders(fakeTenantId)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
