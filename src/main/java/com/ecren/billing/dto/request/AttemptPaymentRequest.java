package com.ecren.billing.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttemptPaymentRequest(
        @Schema(example = "00000000-0000-0000-0000-000000000100")
        @NotNull UUID invoiceId,
        @Schema(example = "my-idempotency-key-001")
        @NotBlank String idempotencyKey
) {}
