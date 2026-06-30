package com.ecren.billing.service;

import com.ecren.billing.common.DemoClock;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.InvoiceLineItem;
import com.ecren.billing.domain.LedgerEntry;
import com.ecren.billing.domain.Payment;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.PlanMetricLimit;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LedgerEntryType;
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
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BillingCycleService {

    private static final Logger log = LoggerFactory.getLogger(BillingCycleService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final PaymentRepository paymentRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final PaymentGateway paymentGateway;
    private final DemoClock clock;

    // Self-proxy so processTenant()'s @Transactional goes through Spring AOP, not this.processTenant() which bypasses the proxy.
    @Lazy
    @Autowired
    private BillingCycleService self;

    public BillingCycleService(SubscriptionRepository subscriptionRepository,
                                PlanRepository planRepository,
                                InvoiceRepository invoiceRepository,
                                LedgerEntryRepository ledgerEntryRepository,
                                PaymentRepository paymentRepository,
                                UsageRecordRepository usageRecordRepository,
                                PaymentGateway paymentGateway,
                                DemoClock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentRepository = paymentRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
    }

    @Scheduled(cron = "0 5 0 * * *")
    @SchedulerLock(name = "billing_cycle", lockAtMostFor = "PT10M")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runBillingCycle() {
        LocalDate today = clock.today();
        List<Subscription> due = subscriptionRepository.findAllByStatusAndCurrentPeriodEnd(SubscriptionStatus.ACTIVE, today);
        for (Subscription subscription : due) {
            try {
                self.processTenant(subscription);
            } catch (Exception e) {
                log.error("Billing cycle failed for tenant {}: {} - {}",
                        subscription.getTenantId(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Runs billing for any ACTIVE subscription whose period has already ended as of
     * {@code asOf}, used by the demo "fast-forward" endpoint instead of waiting for
     * the nightly cron. A subscription may need several cycles to catch up after a
     * large jump, so each one is re-checked and re-processed until its period end
     * is back in the future (capped to avoid runaway loops on bad data).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int runDueCycles(LocalDate asOf) {
        List<Subscription> due = subscriptionRepository
                .findAllByStatusAndCurrentPeriodEndLessThanEqual(SubscriptionStatus.ACTIVE, asOf);
        int processed = 0;
        for (Subscription subscription : due) {
            UUID subscriptionId = subscription.getId();
            for (int cycles = 0; cycles < 24; cycles++) {
                Subscription fresh = subscriptionRepository.findById(subscriptionId).orElse(null);
                if (fresh == null || fresh.getStatus() != SubscriptionStatus.ACTIVE) {
                    break;
                }
                if (fresh.getCurrentPeriodEnd().isAfter(asOf)) {
                    break;
                }
                try {
                    self.processTenant(fresh);
                    processed++;
                } catch (Exception e) {
                    log.error("Demo time-travel billing failed for tenant {}: {} - {}",
                            fresh.getTenantId(), e.getClass().getSimpleName(), e.getMessage());
                    break;
                }
            }
        }
        return processed;
    }

    @Transactional
    public void processTenant(Subscription subscription) {
        LocalDate today = clock.today();
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan not found: " + subscription.getPlanId()));

        Invoice invoice = new Invoice();
        invoice.setTenantId(subscription.getTenantId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setPeriodStart(subscription.getCurrentPeriodStart());
        invoice.setPeriodEnd(subscription.getCurrentPeriodEnd());
        invoice.setDueDate(today);

        InvoiceLineItem baseFee = new InvoiceLineItem();
        baseFee.setInvoice(invoice);
        baseFee.setType(LineItemType.BASE_FEE);
        baseFee.setDescription("Base fee");
        baseFee.setQuantity(1L);
        baseFee.setUnitPriceCents(plan.getBasePriceCents());
        baseFee.setAmountCents(plan.getBasePriceCents());
        invoice.getLineItems().add(baseFee);

        for (PlanMetricLimit limit : plan.getMetricLimits()) {
            UsageMetric metric = limit.getMetric();
            long totalConsumed = usageRecordRepository
                    .findByTenantIdAndSubscriptionIdAndMetric(subscription.getTenantId(), subscription.getId(), metric)
                    .stream()
                    .mapToLong(u -> u.getQuantity())
                    .sum();
            long overage = Math.max(0L, totalConsumed - limit.getIncludedQuantity());
            if (overage > 0) {
                InvoiceLineItem overageItem = new InvoiceLineItem();
                overageItem.setInvoice(invoice);
                overageItem.setType(LineItemType.USAGE_OVERAGE);
                overageItem.setDescription("Overage: " + metric.name());
                overageItem.setQuantity(overage);
                overageItem.setUnitPriceCents(limit.getOveragePricePerUnitCents());
                overageItem.setAmountCents(overage * limit.getOveragePricePerUnitCents());
                invoice.getLineItems().add(overageItem);
            }
        }

        long totalCents = invoice.getLineItems().stream().mapToLong(InvoiceLineItem::getAmountCents).sum();
        invoice.setTotalCents(totalCents);
        invoice.setStatus(InvoiceStatus.FINALIZED);
        invoice.setFinalizedAt(clock.now());
        invoice = invoiceRepository.save(invoice);

        Payment payment = new Payment();
        payment.setTenantId(subscription.getTenantId());
        payment.setInvoiceId(invoice.getId());
        payment.setAmountCents(totalCents);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey("billing-cycle-" + invoice.getId());
        payment = paymentRepository.save(payment);

        GatewayResult result = paymentGateway.charge(subscription.getTenantId(), totalCents, invoice.getId().toString());

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayReference(result.gatewayReference());
            paymentRepository.save(payment);

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(clock.now());
            invoiceRepository.save(invoice);

            LedgerEntry charge = new LedgerEntry();
            charge.setTenantId(subscription.getTenantId());
            charge.setType(LedgerEntryType.CHARGE);
            charge.setAmountCents(totalCents);
            charge.setReferenceId(invoice.getId());
            charge.setDescription("Billing cycle charge");
            ledgerEntryRepository.save(charge);

            LedgerEntry paymentEntry = new LedgerEntry();
            paymentEntry.setTenantId(subscription.getTenantId());
            paymentEntry.setType(LedgerEntryType.PAYMENT);
            paymentEntry.setAmountCents(-totalCents);
            paymentEntry.setReferenceId(invoice.getId());
            paymentEntry.setDescription("Billing cycle payment");
            ledgerEntryRepository.save(paymentEntry);

            subscription.setCurrentPeriodStart(today);
            subscription.setCurrentPeriodEnd(today.plusDays(30));
            if (subscription.getCancelledAt() != null) {
                subscription.setStatus(SubscriptionStatus.CANCELLED);
            }
            // Apply any downgrade requested mid-cycle now that the new period starts.
            if (subscription.getPendingPlanId() != null) {
                subscription.setPlanId(subscription.getPendingPlanId());
                subscription.setPendingPlanId(null);
            }
            subscriptionRepository.save(subscription);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            // The demo wallet won't refill itself, so there's no point waiting for
            // retries — once a renewal can't be charged, the subscription drops.
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(subscription);
        }
    }
}
