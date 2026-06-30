package com.ecren.billing.dto.response;

/**
 * Result of a plan change request.
 * <p>
 * Upgrades apply immediately and {@code invoice} carries the prorated charge.
 * Downgrades are deferred to the next billing cycle, so {@code invoice} is
 * {@code null} and {@code message} explains when the change will take effect.
 */
public record ChangePlanResponse(String message, InvoiceResponse invoice) {}
