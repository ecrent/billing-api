package com.ecren.billing.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID tenantId,
        UUID planId,
        String status,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
