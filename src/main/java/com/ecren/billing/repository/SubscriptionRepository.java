package com.ecren.billing.repository;

import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
    boolean existsByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);
}
