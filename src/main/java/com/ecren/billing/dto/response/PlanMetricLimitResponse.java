package com.ecren.billing.dto.response;

import java.util.UUID;

public record PlanMetricLimitResponse(
        UUID limitId,
        String metric,
        long includedQuantity,
        long overagePricePerUnitCents
) {}
