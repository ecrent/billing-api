package com.ecren.billing;

import com.ecren.billing.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class BaseIT {

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JwtService jwtService;

    protected HttpHeaders userHeaders(UUID tenantId) {
        String token = jwtService.generate(UUID.randomUUID(), "user@test.com", "USER", tenantId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected HttpHeaders adminHeaders() {
        String token = jwtService.generate(UUID.randomUUID(), "admin@test.com", "ADMIN", null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
