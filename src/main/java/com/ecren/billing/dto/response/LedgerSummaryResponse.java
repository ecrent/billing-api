package com.ecren.billing.dto.response;

import java.util.List;

public record LedgerSummaryResponse(
        List<LedgerEntryResponse> entries,
        long balanceCents
) {}
