package com.ecren.billing.service;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.dto.request.CreateSubscriptionRequest;
import com.ecren.billing.dto.response.SubscriptionResponse;
import com.ecren.billing.exception.ConflictException;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.mapper.SubscriptionMapper;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository repository;
    private final PlanRepository planRepository;
    private final SubscriptionMapper mapper;
    private final Set<UUID> protectedTenantIds;

    public SubscriptionService(SubscriptionRepository repository, PlanRepository planRepository,
                               SubscriptionMapper mapper,
                               @Value("${app.protected-tenant-ids:}") String protectedTenantIdsRaw) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.mapper = mapper;
        this.protectedTenantIds = Arrays.stream(protectedTenantIdsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }

    @Transactional
    public SubscriptionResponse subscribe(CreateSubscriptionRequest request) {
        var tenantId = TenantContext.get();

        if (!planRepository.existsById(request.planId())) {
            throw new ResourceNotFoundException("Plan not found: " + request.planId());
        }

        if (repository.existsByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)) {
            throw new ConflictException("Tenant already has an active subscription");
        }

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setPlanId(request.planId());
        subscription.setCurrentPeriodStart(LocalDate.now());
        subscription.setCurrentPeriodEnd(LocalDate.now().plusDays(30));

        return mapper.toResponse(repository.save(subscription));
    }

    public SubscriptionResponse getCurrent() {
        var tenantId = TenantContext.get();
        return repository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription for tenant: " + tenantId));
    }

    @Transactional
    public SubscriptionResponse cancel() {
        var tenantId = TenantContext.get();
        if (protectedTenantIds.contains(tenantId)) {
            throw new ConflictException("This is a demo tenant and cannot be modified");
        }
        Subscription subscription = repository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription for tenant: " + tenantId));

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        return mapper.toResponse(repository.save(subscription));
    }
}
