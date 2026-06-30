package com.ecren.billing.gateway;

import com.ecren.billing.domain.Tenant;
import com.ecren.billing.repository.TenantRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    public static final ThreadLocal<Boolean> SHOULD_FAIL = new ThreadLocal<>();

    private final TenantRepository tenantRepository;

    public MockPaymentGateway(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public GatewayResult charge(UUID tenantId, long amountCents, String reference) {
        Boolean fail = SHOULD_FAIL.get();
        if (Boolean.TRUE.equals(fail)) {
            return new GatewayResult(false, null, "Payment declined");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        if (tenant.getWalletBalanceCents() < amountCents) {
            return new GatewayResult(false, null, "Insufficient funds");
        }

        tenant.setWalletBalanceCents(tenant.getWalletBalanceCents() - amountCents);
        tenantRepository.save(tenant);
        return new GatewayResult(true, "mock-" + reference, "OK");
    }
}
