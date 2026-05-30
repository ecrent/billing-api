package com.ecren.billing.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangePlanRequest(@NotNull UUID newPlanId) {}
