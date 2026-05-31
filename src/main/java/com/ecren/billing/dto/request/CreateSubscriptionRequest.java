package com.ecren.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSubscriptionRequest(
        @Schema(description = "Use ID from GET /api/v1/plans")
        @NotNull UUID planId
) {}
