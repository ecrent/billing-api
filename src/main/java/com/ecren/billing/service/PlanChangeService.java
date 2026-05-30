package com.ecren.billing.service;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.InvoiceLineItem;
import com.ecren.billing.domain.LedgerEntry;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LedgerEntryType;
import com.ecren.billing.domain.enums.LineItemType;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.dto.request.ChangePlanRequest;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.exception.ConflictException;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.gateway.GatewayResult;
import com.ecren.billing.gateway.PaymentGateway;
import com.ecren.billing.mapper.InvoiceMapper;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PlanChangeService {

    private final SubscriptionRepository repository;
    private final PlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentGateway paymentGateway;
    private final InvoiceMapper mapper;

    public PlanChangeService(SubscriptionRepository repository,
                             PlanRepository planRepository,
                             InvoiceRepository invoiceRepository,
                             LedgerEntryRepository ledgerEntryRepository,
                             PaymentGateway paymentGateway,
                             InvoiceMapper mapper) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentGateway = paymentGateway;
        this.mapper = mapper;
    }

    @Transactional
    public ResponseEntity<InvoiceResponse> changePlan(ChangePlanRequest request) {
        UUID tenantId = TenantContext.get();

        Subscription subscription = repository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription for tenant: " + tenantId));

        UUID oldPlanId = subscription.getPlanId();
        UUID newPlanId = request.newPlanId();

        Plan oldPlan = planRepository.findById(oldPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + oldPlanId));
        Plan newPlan = planRepository.findById(newPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + newPlanId));

        if (oldPlanId.equals(newPlanId)) {
            throw new ConflictException("Already on this plan");
        }

        LocalDate periodStart = subscription.getCurrentPeriodStart();
        LocalDate periodEnd = subscription.getCurrentPeriodEnd();
        LocalDate today = LocalDate.now();

        long daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        long remainingDays = ChronoUnit.DAYS.between(today, periodEnd) + 1;

        long creditCents = Math.floorDiv(oldPlan.getBasePriceCents() * remainingDays, daysInPeriod);
        long chargeCents = (long) Math.ceil((double) newPlan.getBasePriceCents() * remainingDays / daysInPeriod);
        long totalCents = chargeCents - creditCents;

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setSubscriptionId(subscription.getId());
        invoice.setPeriodStart(today);
        invoice.setPeriodEnd(periodEnd);
        invoice.setTotalCents(totalCents);

        InvoiceLineItem creditItem = new InvoiceLineItem();
        creditItem.setInvoice(invoice);
        creditItem.setType(LineItemType.PRORATION_CREDIT);
        creditItem.setDescription("Unused days on " + oldPlan.getName());
        creditItem.setQuantity(remainingDays);
        creditItem.setUnitPriceCents(-(creditCents / remainingDays));
        creditItem.setAmountCents(-creditCents);

        InvoiceLineItem chargeItem = new InvoiceLineItem();
        chargeItem.setInvoice(invoice);
        chargeItem.setType(LineItemType.PRORATION_CHARGE);
        chargeItem.setDescription("Remaining days on " + newPlan.getName());
        chargeItem.setQuantity(remainingDays);
        chargeItem.setUnitPriceCents(chargeCents / remainingDays);
        chargeItem.setAmountCents(chargeCents);

        invoice.getLineItems().add(creditItem);
        invoice.getLineItems().add(chargeItem);

        invoice.setStatus(InvoiceStatus.FINALIZED);
        invoice.setFinalizedAt(LocalDateTime.now());
        invoice = invoiceRepository.save(invoice);

        GatewayResult result = paymentGateway.charge(totalCents, invoice.getId().toString());

        if (result.success()) {
            subscription.setPlanId(newPlanId);
            repository.save(subscription);

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            LedgerEntry chargeEntry = new LedgerEntry();
            chargeEntry.setTenantId(tenantId);
            chargeEntry.setType(LedgerEntryType.CHARGE);
            chargeEntry.setAmountCents(totalCents);
            chargeEntry.setDescription("Plan change charge");
            chargeEntry.setReferenceId(invoice.getId());
            ledgerEntryRepository.save(chargeEntry);

            LedgerEntry paymentEntry = new LedgerEntry();
            paymentEntry.setTenantId(tenantId);
            paymentEntry.setType(LedgerEntryType.PAYMENT);
            paymentEntry.setAmountCents(totalCents);
            paymentEntry.setDescription("Payment for plan change");
            paymentEntry.setReferenceId(invoice.getId());
            ledgerEntryRepository.save(paymentEntry);

            return ResponseEntity.ok(mapper.toResponse(invoice));
        } else {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            repository.save(subscription);

            return ResponseEntity.status(402).body(mapper.toResponse(invoice));
        }
    }
}
