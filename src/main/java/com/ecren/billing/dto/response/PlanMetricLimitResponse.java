package com.ecren.billing.dto.response;

import java.util.UUID;

public record PlanMetricLimitResponse(
        UUID id,
        String metric,
        long includedQuantity,
        long overagePricePerUnitCents
) {}
