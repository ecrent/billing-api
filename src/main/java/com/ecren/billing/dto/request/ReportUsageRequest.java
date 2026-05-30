package com.ecren.billing.dto.request;

import com.ecren.billing.domain.enums.UsageMetric;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReportUsageRequest(
        @NotNull UsageMetric metric,
        @Min(1) long quantity,
        @NotBlank String idempotencyKey
) {}
