package com.ecren.billing.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID tenantId,
        UUID invoiceId,
        long amountCents,
        String status,
        String idempotencyKey,
        String gatewayReference,
        LocalDateTime createdAt
) {}
