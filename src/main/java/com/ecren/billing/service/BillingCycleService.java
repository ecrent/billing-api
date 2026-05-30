package com.ecren.billing.service;

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
import java.time.LocalDateTime;
import java.util.List;

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
                                PaymentGateway paymentGateway) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.paymentRepository = paymentRepository;
        this.usageRecordRepository = usageRecordRepository;
        this.paymentGateway = paymentGateway;
    }

    @Scheduled(cron = "0 5 0 * * *")
    @SchedulerLock(name = "billing_cycle", lockAtMostFor = "PT10M")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runBillingCycle() {
        LocalDate today = LocalDate.now();
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

    @Transactional
    public void processTenant(Subscription subscription) {
        LocalDate today = LocalDate.now();
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
        invoice.setFinalizedAt(LocalDateTime.now());
        invoice = invoiceRepository.save(invoice);

        Payment payment = new Payment();
        payment.setTenantId(subscription.getTenantId());
        payment.setInvoiceId(invoice.getId());
        payment.setAmountCents(totalCents);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setIdempotencyKey("billing-cycle-" + invoice.getId());
        payment = paymentRepository.save(payment);

        GatewayResult result = paymentGateway.charge(totalCents, invoice.getId().toString());

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayReference(result.gatewayReference());
            paymentRepository.save(payment);

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(LocalDateTime.now());
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
            paymentEntry.setAmountCents(totalCents);
            paymentEntry.setReferenceId(invoice.getId());
            paymentEntry.setDescription("Billing cycle payment");
            ledgerEntryRepository.save(paymentEntry);

            subscription.setCurrentPeriodStart(today);
            subscription.setCurrentPeriodEnd(today.plusDays(30));
            if (subscription.getCancelledAt() != null) {
                subscription.setStatus(SubscriptionStatus.CANCELLED);
            }
            subscriptionRepository.save(subscription);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            long failedCount = paymentRepository.countByInvoiceIdAndStatus(invoice.getId(), PaymentStatus.FAILED);
            if (failedCount >= 3) {
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                subscriptionRepository.save(subscription);
            }
        }
    }
}
