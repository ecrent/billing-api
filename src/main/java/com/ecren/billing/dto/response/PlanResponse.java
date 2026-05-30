package com.ecren.billing.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String slug,
        long basePriceCents,
        String billingInterval,
        String status,
        List<PlanMetricLimitResponse> metricLimits,
        LocalDateTime createdAt
) {}
