package com.ecren.billing.dto.response;

import java.util.UUID;

public record LineItemResponse(
        UUID lineItemId,
        String type,
        String description,
        long quantity,
        long unitPriceCents,
        long amountCents
) {}
