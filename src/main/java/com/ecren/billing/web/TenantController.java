package com.ecren.billing.web;

import com.ecren.billing.dto.request.CreateTenantRequest;
import com.ecren.billing.dto.response.TenantResponse;
import com.ecren.billing.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @Valid @RequestBody CreateTenantRequest request,
            UriComponentsBuilder uriBuilder) {
        TenantResponse response = service.create(request);
        var location = uriBuilder.path("/api/v1/tenants/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public TenantResponse getById(@PathVariable UUID id) {
        return service.findById(id);
    }
}
