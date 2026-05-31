package com.ecren.billing.web;

import com.ecren.billing.dto.request.CreateTenantRequest;
import com.ecren.billing.dto.response.TenantResponse;
import com.ecren.billing.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Tag(name = "1. Tenants")
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @Operation(summary = "Create tenant")
    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request,
            UriComponentsBuilder uriBuilder) {
        TenantResponse response = service.create(request);
        var location = uriBuilder.path("/api/v1/tenants/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get tenant by ID")
    @GetMapping("/{tenantId}")
    public TenantResponse getById(
            @Parameter(example = "00000000-0000-0000-0000-000000000001")
            @PathVariable UUID tenantId) {
        return service.findById(tenantId);
    }
}
