package com.ecren.billing.gateway;

import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

    public static final ThreadLocal<Boolean> SHOULD_FAIL = new ThreadLocal<>();

    @Override
    public GatewayResult charge(long amountCents, String reference) {
        Boolean fail = SHOULD_FAIL.get();
        if (Boolean.TRUE.equals(fail)) {
            return new GatewayResult(false, null, "Payment declined");
        }
        return new GatewayResult(true, "mock-" + reference, "OK");
    }
}
