package com.ecren.billing.gateway;

import com.ecren.billing.domain.Tenant;
import com.ecren.billing.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MockPaymentGatewayTest {

    @Mock
    TenantRepository tenantRepository;

    @InjectMocks
    MockPaymentGateway gateway;

    @AfterEach
    void tearDown() {
        MockPaymentGateway.SHOULD_FAIL.remove();
    }

    @Test
    void charge_givenSufficientBalance_thenSucceedsAndDebitsWallet() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 100_000L);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        GatewayResult result = gateway.charge(tenantId, 2900L, "ref-1");

        assertThat(result.success()).isTrue();
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        assertThat(captor.getValue().getWalletBalanceCents()).isEqualTo(97_100L);
    }

    @Test
    void charge_givenInsufficientBalance_thenDeclinesAndLeavesWalletUntouched() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 1_000L);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        GatewayResult result = gateway.charge(tenantId, 2900L, "ref-2");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Insufficient funds");
        verify(tenantRepository, never()).save(any());
        assertThat(tenant.getWalletBalanceCents()).isEqualTo(1_000L);
    }

    @Test
    void charge_givenExactBalance_thenSucceedsAndZeroesWallet() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, 2900L);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        GatewayResult result = gateway.charge(tenantId, 2900L, "ref-3");

        assertThat(result.success()).isTrue();
        assertThat(tenant.getWalletBalanceCents()).isEqualTo(0L);
    }

    @Test
    void charge_givenShouldFailFlagSet_thenDeclinesWithoutTouchingWallet() {
        MockPaymentGateway.SHOULD_FAIL.set(true);
        UUID tenantId = UUID.randomUUID();

        GatewayResult result = gateway.charge(tenantId, 2900L, "ref-4");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Payment declined");
        verify(tenantRepository, never()).findById(any());
    }

    private Tenant tenant(UUID id, long balanceCents) {
        Tenant t = new Tenant();
        t.setWalletBalanceCents(balanceCents);
        return t;
    }
}
