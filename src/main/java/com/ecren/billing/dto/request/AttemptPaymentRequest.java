package com.ecren.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttemptPaymentRequest(
        @NotNull UUID invoiceId,
        @NotBlank String idempotencyKey
) {}
