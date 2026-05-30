package com.ecren.billing.service;

import com.ecren.billing.domain.Tenant;
import com.ecren.billing.dto.request.CreateTenantRequest;
import com.ecren.billing.dto.response.TenantResponse;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.mapper.TenantMapper;
import com.ecren.billing.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository repository;
    private final TenantMapper mapper;

    public TenantService(TenantRepository repository, TenantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public TenantResponse create(CreateTenantRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setEmail(request.email());
        return mapper.toResponse(repository.save(tenant));
    }

    public TenantResponse findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }
}
