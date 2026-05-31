package com.ecren.billing.web;

import com.ecren.billing.TestcontainersConfiguration;
import com.ecren.billing.dto.request.CreateTenantRequest;
import com.ecren.billing.dto.response.TenantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TenantControllerIT.PingController.class})
class TenantControllerIT {

    @Autowired
    TestRestTemplate rest;

    @TestConfiguration
    static class PingController {
        @RestController
        class PingEndpoint {
            @GetMapping("/api/v1/test/ping")
            String ping() {
                return "pong";
            }
        }
    }

    @Test
    void createTenant_givenValidRequest_thenReturns201WithLocation() {
        var request = new CreateTenantRequest("Acme Corp", "acme@example.com");

        ResponseEntity<TenantResponse> response = rest.postForEntity(
                "/api/v1/tenants", request, TenantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        TenantResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.tenantId()).isNotNull();
        assertThat(body.name()).isEqualTo("Acme Corp");
        assertThat(body.email()).isEqualTo("acme@example.com");
        assertThat(body.status()).isEqualTo("ACTIVE");
        assertThat(body.createdAt()).isNotNull();
        assertThat(body.updatedAt()).isNotNull();
    }

    @Test
    void createTenant_givenDuplicateEmail_thenReturns400() {
        var request = new CreateTenantRequest("Dupe Corp", "dupe@example.com");
        rest.postForEntity("/api/v1/tenants", request, TenantResponse.class);

        ResponseEntity<String> response = rest.postForEntity(
                "/api/v1/tenants", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getTenant_givenExistingId_thenReturns200() {
        var created = rest.postForEntity(
                "/api/v1/tenants",
                new CreateTenantRequest("Get Corp", "getcorp@example.com"),
                TenantResponse.class);
        UUID id = created.getBody().tenantId();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", id.toString());
        ResponseEntity<TenantResponse> response = rest.exchange(
                "/api/v1/tenants/" + id, HttpMethod.GET,
                new HttpEntity<>(headers), TenantResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().tenantId()).isEqualTo(id);
    }

    @Test
    void getTenant_givenUnknownId_thenReturns404ProblemDetail() {
        UUID unknownId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", unknownId.toString());

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/tenants/" + unknownId, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void tenantScopedEndpoint_givenMissingHeader_thenReturns400ProblemDetail() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/v1/test/ping", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void tenantScopedEndpoint_givenUnknownTenantId_thenReturns404ProblemDetail() {
        UUID unknownId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", unknownId.toString());

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/test/ping", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }
}
