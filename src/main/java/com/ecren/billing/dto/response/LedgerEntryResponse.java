package com.ecren.billing.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID ledgerEntryId,
        String type,
        long amountCents,
        String description,
        UUID referenceId,
        LocalDateTime createdAt
) {}
