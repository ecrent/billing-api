package com.ecren.billing.service;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.UsageRecord;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.dto.request.ReportUsageRequest;
import com.ecren.billing.dto.response.UsageRecordResponse;
import com.ecren.billing.dto.response.UsageSummaryResponse;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.mapper.UsageMapper;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.UsageRecordRepository;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UsageService {

    private final UsageRecordRepository repository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UsageMapper mapper;
    private final EntityManager entityManager;

    public UsageService(UsageRecordRepository repository, SubscriptionRepository subscriptionRepository,
                        PlanRepository planRepository, UsageMapper mapper, EntityManager entityManager) {
        this.repository = repository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public ReportResult report(ReportUsageRequest request) {
        UUID tenantId = TenantContext.get();
        Subscription subscription = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant"));

        UsageRecord record = new UsageRecord();
        record.setTenantId(tenantId);
        record.setSubscriptionId(subscription.getId());
        record.setMetric(request.metric());
        record.setQuantity(request.quantity());
        record.setIdempotencyKey(request.idempotencyKey());

        return repository.findByIdempotencyKeyAndTenantId(request.idempotencyKey(), tenantId)
                .map(existing -> new ReportResult(mapper.toResponse(existing), false))
                .orElseGet(() -> {
                    UsageRecord saved = repository.save(record);
                    entityManager.flush();
                    return new ReportResult(mapper.toResponse(saved), true);
                });
    }

    public UsageSummaryResponse getSummary() {
        UUID tenantId = TenantContext.get();
        Subscription subscription = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for tenant"));

        var plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        Map<UsageMetric, Long> includedByMetric = plan.getMetricLimits().stream()
                .collect(Collectors.toMap(PlanMetricLimit::getMetric, PlanMetricLimit::getIncludedQuantity));

        LocalDateTime periodStart = subscription.getCurrentPeriodStart().atStartOfDay();
        LocalDateTime periodEnd = subscription.getCurrentPeriodEnd().atTime(23, 59, 59);

        List<UsageSummaryResponse.MetricSummary> metrics = Arrays.stream(UsageMetric.values())
                .map(metric -> {
                    List<UsageRecord> records = repository.findByTenantIdAndSubscriptionIdAndMetric(
                            tenantId, subscription.getId(), metric);
                    long consumed = records.stream()
                            .filter(r -> r.getRecordedAt() != null
                                    && !r.getRecordedAt().isBefore(periodStart)
                                    && !r.getRecordedAt().isAfter(periodEnd))
                            .mapToLong(UsageRecord::getQuantity)
                            .sum();
                    long included = includedByMetric.getOrDefault(metric, 0L);
                    long overage = Math.max(0, consumed - included);
                    return new UsageSummaryResponse.MetricSummary(metric.name(), consumed, included, overage);
                })
                .toList();

        return new UsageSummaryResponse(metrics);
    }

    public record ReportResult(UsageRecordResponse response, boolean isNew) {}
}
