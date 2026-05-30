package com.ecren.billing.gateway;

public record GatewayResult(boolean success, String gatewayReference, String message) {}
