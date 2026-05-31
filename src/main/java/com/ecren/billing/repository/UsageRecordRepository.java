package com.ecren.billing.repository;

import com.ecren.billing.domain.UsageRecord;
import com.ecren.billing.domain.enums.UsageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    Optional<UsageRecord> findByIdempotencyKeyAndTenantId(String key, UUID tenantId);

    @Query("SELECT u FROM UsageRecord u WHERE u.tenantId = :tenantId AND u.subscriptionId = :subscriptionId AND u.metric = :metric")
    List<UsageRecord> findByTenantIdAndSubscriptionIdAndMetric(UUID tenantId, UUID subscriptionId, UsageMetric metric);
}
