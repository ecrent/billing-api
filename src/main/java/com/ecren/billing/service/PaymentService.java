package com.ecren.billing.service;

import com.ecren.billing.common.TenantContext;
import com.ecren.billing.domain.LedgerEntry;
import com.ecren.billing.domain.Payment;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LedgerEntryType;
import com.ecren.billing.domain.enums.PaymentStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.dto.request.AttemptPaymentRequest;
import com.ecren.billing.dto.response.LedgerEntryResponse;
import com.ecren.billing.dto.response.LedgerSummaryResponse;
import com.ecren.billing.dto.response.PaymentResponse;
import com.ecren.billing.exception.ConflictException;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.gateway.GatewayResult;
import com.ecren.billing.gateway.PaymentGateway;
import com.ecren.billing.mapper.LedgerMapper;
import com.ecren.billing.mapper.PaymentMapper;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PaymentRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    public record PaymentResult(PaymentResponse response, boolean isNew) {}

    private final PaymentRepository repository;
    private final InvoiceRepository invoiceRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentMapper mapper;
    private final LedgerMapper ledgerMapper;

    public PaymentService(PaymentRepository repository,
                          InvoiceRepository invoiceRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          SubscriptionRepository subscriptionRepository,
                          PaymentGateway paymentGateway,
                          PaymentMapper mapper,
                          LedgerMapper ledgerMapper) {
        this.repository = repository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentGateway = paymentGateway;
        this.mapper = mapper;
        this.ledgerMapper = ledgerMapper;
    }

    @Transactional
    public PaymentResult attemptPayment(AttemptPaymentRequest request) {
        UUID tenantId = TenantContext.get();

        return repository.findByIdempotencyKeyAndTenantId(request.idempotencyKey(), tenantId)
                .map(existing -> new PaymentResult(mapper.toResponse(existing), false))
                .orElseGet(() -> new PaymentResult(processNewPayment(request, tenantId), true));
    }

    private PaymentResponse processNewPayment(AttemptPaymentRequest request, UUID tenantId) {
        var invoice = invoiceRepository.findByIdAndTenantId(request.invoiceId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.FINALIZED) {
            throw new ConflictException("Invoice is not in FINALIZED status");
        }

        Payment payment = new Payment();
        payment.setTenantId(tenantId);
        payment.setInvoiceId(invoice.getId());
        payment.setAmountCents(invoice.getTotalCents());
        payment.setIdempotencyKey(request.idempotencyKey());
        payment = repository.save(payment);

        GatewayResult result = paymentGateway.charge(invoice.getTotalCents(), payment.getId().toString());

        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setGatewayReference(result.gatewayReference());
            payment = repository.save(payment);

            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            LedgerEntry charge = new LedgerEntry();
            charge.setTenantId(tenantId);
            charge.setType(LedgerEntryType.CHARGE);
            charge.setAmountCents(invoice.getTotalCents());
            charge.setDescription("Charge for invoice " + invoice.getId());
            charge.setReferenceId(invoice.getId());
            ledgerEntryRepository.save(charge);

            LedgerEntry paymentEntry = new LedgerEntry();
            paymentEntry.setTenantId(tenantId);
            paymentEntry.setType(LedgerEntryType.PAYMENT);
            paymentEntry.setAmountCents(-invoice.getTotalCents());
            paymentEntry.setDescription("Payment for invoice " + invoice.getId());
            paymentEntry.setReferenceId(invoice.getId());
            ledgerEntryRepository.save(paymentEntry);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment = repository.save(payment);

            long failedCount = repository.countByInvoiceIdAndStatus(invoice.getId(), PaymentStatus.FAILED);
            if (failedCount >= 3) {
                subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE)
                        .ifPresent(sub -> {
                            sub.setStatus(SubscriptionStatus.PAST_DUE);
                            subscriptionRepository.save(sub);
                        });
            }
        }

        return mapper.toResponse(payment);
    }

    public LedgerSummaryResponse getLedger() {
        UUID tenantId = TenantContext.get();
        List<LedgerEntry> entries = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        long balanceCents = entries.stream().mapToLong(LedgerEntry::getAmountCents).sum();
        List<LedgerEntryResponse> responses = entries.stream().map(ledgerMapper::toResponse).toList();
        return new LedgerSummaryResponse(responses, balanceCents);
    }

    public PaymentResponse getPayment(UUID id) {
        UUID tenantId = TenantContext.get();
        Payment payment = repository.findById(id)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return mapper.toResponse(payment);
    }
}
