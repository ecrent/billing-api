package com.ecren.billing.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID tenantId,
        UUID subscriptionId,
        String status,
        LocalDate periodStart,
        LocalDate periodEnd,
        long totalCents,
        LocalDate dueDate,
        LocalDateTime finalizedAt,
        LocalDateTime paidAt,
        List<LineItemResponse> lineItems,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
