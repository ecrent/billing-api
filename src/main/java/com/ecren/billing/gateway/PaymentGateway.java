package com.ecren.billing.gateway;

public interface PaymentGateway {
    GatewayResult charge(long amountCents, String reference);
}
