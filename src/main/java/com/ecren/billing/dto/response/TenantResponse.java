package com.ecren.billing.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID tenantId,
        String name,
        String email,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
