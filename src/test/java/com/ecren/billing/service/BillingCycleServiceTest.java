package com.ecren.billing.service;

import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.Payment;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.UsageRecord;
import com.ecren.billing.domain.enums.LineItemType;
import com.ecren.billing.domain.enums.PaymentStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.domain.enums.UsageMetric;
import com.ecren.billing.gateway.GatewayResult;
import com.ecren.billing.gateway.PaymentGateway;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PaymentRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingCycleServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    PlanRepository planRepository;
    @Mock
    InvoiceRepository invoiceRepository;
    @Mock
    LedgerEntryRepository ledgerEntryRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    UsageRecordRepository usageRecordRepository;
    @Mock
    PaymentGateway paymentGateway;

    @InjectMocks
    BillingCycleService service;

    UUID tenantId;
    UUID planId;
    UUID subscriptionId;
    Subscription subscription;
    Plan plan;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();

        subscription = buildSubscription(tenantId, planId, subscriptionId, null);
        plan = buildPlan(planId, 1000L);
    }

    @Test
    void processTenant_givenNoOverage_thenBaseFeeOnly() {
        PlanMetricLimit limit = buildLimit(UsageMetric.API_CALLS, 100L, 5L);
        plan.setMetricLimits(List.of(limit));

        UsageRecord usage = new UsageRecord();
        usage.setQuantity(50L);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(usageRecordRepository.findByTenantIdAndSubscriptionIdAndMetric(tenantId, subscriptionId, UsageMetric.API_CALLS))
                .thenReturn(List.of(usage));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setPaymentId(p);
            return p;
        });
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-1", "OK"));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTenant(subscription);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, atLeastOnce()).save(captor.capture());
        Invoice saved = captor.getAllValues().get(0);

        assertThat(saved.getLineItems()).hasSize(1);
        assertThat(saved.getLineItems().get(0).getType()).isEqualTo(LineItemType.BASE_FEE);
        assertThat(saved.getTotalCents()).isEqualTo(1000L);
    }

    @Test
    void processTenant_givenOverageOnMetric_thenOverageLineItemIncluded() {
        PlanMetricLimit limit = buildLimit(UsageMetric.API_CALLS, 100L, 5L);
        plan.setMetricLimits(List.of(limit));

        UsageRecord usage = new UsageRecord();
        usage.setQuantity(150L); // 50 over

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(usageRecordRepository.findByTenantIdAndSubscriptionIdAndMetric(tenantId, subscriptionId, UsageMetric.API_CALLS))
                .thenReturn(List.of(usage));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setPaymentId(p);
            return p;
        });
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-2", "OK"));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTenant(subscription);

        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, atLeastOnce()).save(captor.capture());
        Invoice saved = captor.getAllValues().get(0);

        assertThat(saved.getLineItems()).hasSize(2);
        assertThat(saved.getLineItems().stream().anyMatch(li -> li.getType() == LineItemType.USAGE_OVERAGE)).isTrue();
        long overageAmount = saved.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.USAGE_OVERAGE)
                .mapToLong(li -> li.getAmountCents())
                .sum();
        assertThat(overageAmount).isEqualTo(250L); // 50 * 5
        assertThat(saved.getTotalCents()).isEqualTo(1250L); // 1000 + 250
    }

    @Test
    void processTenant_givenPaymentSuccess_thenPeriodRolledForward() {
        plan.setMetricLimits(List.of());

        LocalDate today = LocalDate.now();
        subscription.setCurrentPeriodStart(today.minusDays(30));
        subscription.setCurrentPeriodEnd(today);

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setPaymentId(p);
            return p;
        });
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-3", "OK"));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTenant(subscription);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subCaptor.capture());
        Subscription saved = subCaptor.getValue();

        assertThat(saved.getCurrentPeriodStart()).isEqualTo(today);
        assertThat(saved.getCurrentPeriodEnd()).isEqualTo(today.plusDays(30));
    }

    @Test
    void processTenant_givenCancelledAt_thenStatusCancelledAfterPayment() {
        plan.setMetricLimits(List.of());
        subscription.setCancelledAt(java.time.LocalDateTime.now().minusDays(1));

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setPaymentId(p);
            return p;
        });
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-4", "OK"));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTenant(subscription);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    void processTenant_givenPaymentFailure3Times_thenPastDue() {
        plan.setMetricLimits(List.of());

        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setPaymentId(p);
            return p;
        });
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(false, null, "Declined"));
        when(paymentRepository.countByInvoiceIdAndStatus(any(), eq(PaymentStatus.FAILED))).thenReturn(3L);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTenant(subscription);

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void runBillingCycle_givenOneTenantFails_thenOtherTenantsStillProcessed() {
        LocalDate today = LocalDate.now();

        Subscription sub1 = buildSubscription(UUID.randomUUID(), planId, UUID.randomUUID(), null);
        sub1.setCurrentPeriodEnd(today);
        Subscription sub2 = buildSubscription(UUID.randomUUID(), planId, UUID.randomUUID(), null);
        sub2.setCurrentPeriodEnd(today);

        when(subscriptionRepository.findAllByStatusAndCurrentPeriodEnd(SubscriptionStatus.ACTIVE, today))
                .thenReturn(List.of(sub1, sub2));

        // Use a spy of the service so self-proxy works in unit test context
        BillingCycleService spy = org.mockito.Mockito.spy(service);
        // sub1 throws, sub2 succeeds
        org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                .doCallRealMethod()
                .when(spy).processTenant(any());
        // Wire self to the spy via reflection
        setSelf(service, spy);

        plan.setMetricLimits(List.of());
        when(planRepository.findById(any())).thenReturn(Optional.of(plan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            setPaymentId(p);
            return p;
        });
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-5", "OK"));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Should not throw
        service.runBillingCycle();

        // sub2 should still be processed: subscriptionRepository.save called once (for sub2 only)
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    // --- helpers ---

    private Subscription buildSubscription(UUID tenantId, UUID planId, UUID subId, java.time.LocalDateTime cancelledAt) {
        Subscription s = new Subscription();
        setField(s, com.ecren.billing.common.BaseEntity.class, "id", subId);
        s.setTenantId(tenantId);
        s.setPlanId(planId);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setCurrentPeriodStart(LocalDate.now().minusDays(30));
        s.setCurrentPeriodEnd(LocalDate.now());
        s.setCancelledAt(cancelledAt);
        return s;
    }

    private Plan buildPlan(UUID id, long basePriceCents) {
        Plan p = new Plan();
        setField(p, Plan.class, "id", id);
        p.setName("Basic");
        p.setSlug("basic");
        p.setBasePriceCents(basePriceCents);
        p.setMetricLimits(new java.util.ArrayList<>());
        return p;
    }

    private PlanMetricLimit buildLimit(UsageMetric metric, long included, long overagePrice) {
        PlanMetricLimit l = new PlanMetricLimit();
        l.setMetric(metric);
        l.setIncludedQuantity(included);
        l.setOveragePricePerUnitCents(overagePrice);
        return l;
    }

    private Invoice assignId(Invoice invoice) {
        setField(invoice, com.ecren.billing.common.BaseEntity.class, "id", UUID.randomUUID());
        return invoice;
    }

    private void setPaymentId(Payment p) {
        try {
            var field = Payment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setField(Object target, Class<?> declaringClass, String fieldName, Object value) {
        try {
            var field = declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setSelf(BillingCycleService target, BillingCycleService selfValue) {
        try {
            var field = BillingCycleService.class.getDeclaredField("self");
            field.setAccessible(true);
            field.set(target, selfValue);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
