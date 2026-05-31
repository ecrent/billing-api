package com.ecren.billing.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record UsageRecordResponse(
        UUID usageRecordId,
        UUID tenantId,
        UUID subscriptionId,
        String metric,
        long quantity,
        String idempotencyKey,
        LocalDateTime recordedAt
) {}
