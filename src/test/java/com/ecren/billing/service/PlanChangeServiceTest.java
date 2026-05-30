package com.ecren.billing.service;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LineItemType;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.dto.request.ChangePlanRequest;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.dto.response.LineItemResponse;
import com.ecren.billing.gateway.GatewayResult;
import com.ecren.billing.gateway.PaymentGateway;
import com.ecren.billing.mapper.InvoiceMapper;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanChangeServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;
    @Mock
    PlanRepository planRepository;
    @Mock
    InvoiceRepository invoiceRepository;
    @Mock
    LedgerEntryRepository ledgerEntryRepository;
    @Mock
    PaymentGateway paymentGateway;
    @Mock
    InvoiceMapper invoiceMapper;

    @InjectMocks
    PlanChangeService service;

    UUID tenantId;
    UUID oldPlanId;
    UUID newPlanId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        oldPlanId = UUID.randomUUID();
        newPlanId = UUID.randomUUID();
        TenantContext.set(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void changePlan_givenDayOneOfPeriod_thenCreditEqualsFullPrice() {
        // remaining=30, period=30 (today is day 1 of a 30-day period)
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today;
        LocalDate periodEnd = today.plusDays(29); // inclusive: 30 days total

        Subscription subscription = buildSubscription(oldPlanId, periodStart, periodEnd);
        Plan oldPlan = buildPlan(oldPlanId, "Basic", 3000L);
        Plan newPlan = buildPlan(newPlanId, "Pro", 6000L);

        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(planRepository.findById(oldPlanId)).thenReturn(Optional.of(oldPlan));
        when(planRepository.findById(newPlanId)).thenReturn(Optional.of(newPlan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-001", "OK"));
        when(invoiceMapper.toResponse(any(Invoice.class))).thenReturn(stubInvoiceResponse());

        ResponseEntity<InvoiceResponse> result = service.changePlan(new ChangePlanRequest(newPlanId));

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, atLeastOnce()).save(invoiceCaptor.capture());

        // With 30 remaining days out of 30-day period:
        // creditCents = floor(3000 * 30 / 30) = 3000
        // chargeCents = ceil(6000.0 * 30 / 30) = 6000
        // totalCents = 6000 - 3000 = 3000
        Invoice savedInvoice = invoiceCaptor.getAllValues().get(0);
        assertThat(savedInvoice.getLineItems()).hasSize(2);

        var credit = savedInvoice.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.PRORATION_CREDIT).findFirst().orElseThrow();
        var charge = savedInvoice.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.PRORATION_CHARGE).findFirst().orElseThrow();

        assertThat(credit.getAmountCents()).isEqualTo(-3000L);
        assertThat(charge.getAmountCents()).isEqualTo(6000L);
        assertThat(savedInvoice.getTotalCents()).isEqualTo(3000L);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void changePlan_givenMidCycle_thenProratesCorrectly() {
        // day 10 of 30-day period, remaining=21 days
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusDays(9); // 10 days ago = day 1
        LocalDate periodEnd = today.plusDays(20);   // 21 days remaining (today inclusive)

        Subscription subscription = buildSubscription(oldPlanId, periodStart, periodEnd);
        Plan oldPlan = buildPlan(oldPlanId, "Basic", 3000L);
        Plan newPlan = buildPlan(newPlanId, "Pro", 6000L);

        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(planRepository.findById(oldPlanId)).thenReturn(Optional.of(oldPlan));
        when(planRepository.findById(newPlanId)).thenReturn(Optional.of(newPlan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-002", "OK"));
        when(invoiceMapper.toResponse(any(Invoice.class))).thenReturn(stubInvoiceResponse());

        service.changePlan(new ChangePlanRequest(newPlanId));

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, atLeastOnce()).save(invoiceCaptor.capture());

        // daysInPeriod = between(periodStart, periodEnd) + 1 = 29 + 1 = 30
        // remainingDays = between(today, periodEnd) + 1 = 20 + 1 = 21
        // creditCents = floor(3000 * 21 / 30) = floor(2100) = 2100
        // chargeCents = ceil(6000.0 * 21 / 30) = ceil(4200.0) = 4200
        // totalCents = 4200 - 2100 = 2100
        Invoice savedInvoice = invoiceCaptor.getAllValues().get(0);

        var credit = savedInvoice.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.PRORATION_CREDIT).findFirst().orElseThrow();
        var charge = savedInvoice.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.PRORATION_CHARGE).findFirst().orElseThrow();

        assertThat(credit.getAmountCents()).isEqualTo(-2100L);
        assertThat(charge.getAmountCents()).isEqualTo(4200L);
        assertThat(savedInvoice.getTotalCents()).isEqualTo(2100L);
    }

    @Test
    void changePlan_givenLastDay_thenRemainingIsOne() {
        // last day of period: remainingDays = 1
        LocalDate today = LocalDate.now();
        LocalDate periodStart = today.minusDays(29);
        LocalDate periodEnd = today; // today is last day

        Subscription subscription = buildSubscription(oldPlanId, periodStart, periodEnd);
        Plan oldPlan = buildPlan(oldPlanId, "Basic", 3000L);
        Plan newPlan = buildPlan(newPlanId, "Pro", 6000L);

        when(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(subscription));
        when(planRepository.findById(oldPlanId)).thenReturn(Optional.of(oldPlan));
        when(planRepository.findById(newPlanId)).thenReturn(Optional.of(newPlan));
        when(invoiceRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentGateway.charge(anyLong(), anyString())).thenReturn(new GatewayResult(true, "ref-003", "OK"));
        when(invoiceMapper.toResponse(any(Invoice.class))).thenReturn(stubInvoiceResponse());

        service.changePlan(new ChangePlanRequest(newPlanId));

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, atLeastOnce()).save(invoiceCaptor.capture());

        // daysInPeriod = between(periodStart, periodEnd) + 1 = 29 + 1 = 30
        // remainingDays = between(today, periodEnd) + 1 = 0 + 1 = 1
        // creditCents = floor(3000 * 1 / 30) = 100
        // chargeCents = ceil(6000.0 * 1 / 30) = ceil(200.0) = 200
        // totalCents = 200 - 100 = 100
        Invoice savedInvoice = invoiceCaptor.getAllValues().get(0);

        var credit = savedInvoice.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.PRORATION_CREDIT).findFirst().orElseThrow();
        var charge = savedInvoice.getLineItems().stream()
                .filter(li -> li.getType() == LineItemType.PRORATION_CHARGE).findFirst().orElseThrow();

        assertThat(credit.getQuantity()).isEqualTo(1L);
        assertThat(credit.getAmountCents()).isEqualTo(-100L);
        assertThat(charge.getQuantity()).isEqualTo(1L);
        assertThat(charge.getAmountCents()).isEqualTo(200L);
        assertThat(savedInvoice.getTotalCents()).isEqualTo(100L);
    }

    private Subscription buildSubscription(UUID planId, LocalDate periodStart, LocalDate periodEnd) {
        Subscription s = new Subscription();
        s.setTenantId(tenantId);
        s.setPlanId(planId);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setCurrentPeriodStart(periodStart);
        s.setCurrentPeriodEnd(periodEnd);
        return s;
    }

    private Plan buildPlan(UUID id, String name, long basePriceCents) {
        Plan p = new Plan();
        // Plan has no setter for id (generated), use reflection or just mock
        // We use a spy-like approach: need a way to set the id
        // Since Plan.id has no public setter, we set it via the private field approach
        try {
            var field = Plan.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(p, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        p.setName(name);
        p.setBasePriceCents(basePriceCents);
        return p;
    }

    private Invoice assignId(Invoice invoice) {
        try {
            var field = com.ecren.billing.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(invoice, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return invoice;
    }

    private InvoiceResponse stubInvoiceResponse() {
        return new InvoiceResponse(UUID.randomUUID(), tenantId, UUID.randomUUID(),
                "PAID", LocalDate.now(), LocalDate.now().plusDays(29), 3000L,
                null, null, null, List.of(), null, null);
    }
}
