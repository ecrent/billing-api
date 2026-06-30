package com.ecren.billing.gateway;

import java.util.UUID;

public interface PaymentGateway {
    GatewayResult charge(UUID tenantId, long amountCents, String reference);
}
