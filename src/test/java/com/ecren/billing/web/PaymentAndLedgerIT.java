package com.ecren.billing.web;

import com.ecren.billing.BaseIT;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.LedgerEntry;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LedgerEntryType;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.dto.request.AttemptPaymentRequest;
import com.ecren.billing.dto.response.LedgerSummaryResponse;
import com.ecren.billing.dto.response.PaymentResponse;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAndLedgerIT extends BaseIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    private Tenant tenant;
    private Invoice finalizedInvoice;

    @BeforeEach
    void setUp() {
        cleanup();

        tenant = new Tenant();
        tenant.setName("Payment Corp");
        tenant.setEmail("payments@example.com");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        Plan plan = new Plan();
        plan.setName("Basic");
        plan.setSlug("basic-payment");
        plan.setBasePriceCents(1000L);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(PlanStatus.ACTIVE);
        plan = planRepository.save(plan);

        Subscription subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlanId(plan.getId());
        subscription.setCurrentPeriodStart(LocalDate.now());
        subscription.setCurrentPeriodEnd(LocalDate.now().plusDays(30));
        subscription = subscriptionRepository.save(subscription);

        finalizedInvoice = new Invoice();
        finalizedInvoice.setTenantId(tenant.getId());
        finalizedInvoice.setSubscriptionId(subscription.getId());
        finalizedInvoice.setStatus(InvoiceStatus.FINALIZED);
        finalizedInvoice.setPeriodStart(LocalDate.now().withDayOfMonth(1));
        finalizedInvoice.setPeriodEnd(LocalDate.now().withDayOfMonth(28));
        finalizedInvoice.setTotalCents(1000L);
        finalizedInvoice.setFinalizedAt(LocalDateTime.now());
        finalizedInvoice = invoiceRepository.save(finalizedInvoice);
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        jdbc.execute("DELETE FROM payments");
        jdbc.execute("DELETE FROM ledger_entries");
        jdbc.execute("DELETE FROM invoice_line_items");
        jdbc.execute("DELETE FROM invoices");
        jdbc.execute("DELETE FROM usage_records");
        jdbc.execute("DELETE FROM subscriptions");
        jdbc.execute("DELETE FROM plan_metric_limits");
        jdbc.execute("DELETE FROM plans");
        jdbc.execute("DELETE FROM tenants");
    }

    @Test
    void attemptPayment_givenFinalizedInvoice_thenReturns201AndLedgerHasTwoEntries() {
        AttemptPaymentRequest request = new AttemptPaymentRequest(finalizedInvoice.getId(), "idem-key-1");

        ResponseEntity<PaymentResponse> response = rest.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, userHeaders(tenant.getId())),
                PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("SUCCEEDED");
        assertThat(body.invoiceId()).isEqualTo(finalizedInvoice.getId());

        ResponseEntity<LedgerSummaryResponse> ledger = rest.exchange(
                "/api/v1/ledger", HttpMethod.GET,
                new HttpEntity<>(userHeaders(tenant.getId())),
                LedgerSummaryResponse.class);

        assertThat(ledger.getStatusCode()).isEqualTo(HttpStatus.OK);
        LedgerSummaryResponse ledgerBody = ledger.getBody();
        assertThat(ledgerBody).isNotNull();
        assertThat(ledgerBody.entries()).hasSize(2);
        assertThat(ledgerBody.balanceCents()).isEqualTo(0L);
    }

    @Test
    void attemptPayment_givenDuplicateIdempotencyKey_thenReturns200WithSamePayment() {
        AttemptPaymentRequest request = new AttemptPaymentRequest(finalizedInvoice.getId(), "idem-key-dup");
        HttpHeaders headers = userHeaders(tenant.getId());

        ResponseEntity<PaymentResponse> first = rest.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, headers), PaymentResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<PaymentResponse> second = rest.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, headers), PaymentResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(second.getBody()).isNotNull();
        assertThat(second.getBody().paymentId()).isEqualTo(first.getBody().paymentId());

        ResponseEntity<LedgerSummaryResponse> ledger = rest.exchange(
                "/api/v1/ledger", HttpMethod.GET,
                new HttpEntity<>(headers), LedgerSummaryResponse.class);
        assertThat(ledger.getBody()).isNotNull();
        assertThat(ledger.getBody().entries()).hasSize(2);
    }

    @Test
    void attemptPayment_givenPaymentFailure_thenReturns201WithFailedStatus() {
        AttemptPaymentRequest request = new AttemptPaymentRequest(finalizedInvoice.getId(), "idem-key-fail");

        HttpHeaders headers = userHeaders(tenant.getId());
        headers.set("X-Mock-Gateway-Result", "FAIL");

        ResponseEntity<PaymentResponse> response = rest.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, headers), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PaymentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("FAILED");

        Invoice invoice = invoiceRepository.findById(finalizedInvoice.getId()).orElseThrow();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.FINALIZED);
    }

    @Test
    void attemptPayment_givenNonFinalizedInvoice_thenReturns409() {
        Invoice draftInvoice = new Invoice();
        draftInvoice.setTenantId(tenant.getId());
        draftInvoice.setSubscriptionId(finalizedInvoice.getSubscriptionId());
        draftInvoice.setStatus(InvoiceStatus.DRAFT);
        draftInvoice.setPeriodStart(LocalDate.now().withDayOfMonth(1));
        draftInvoice.setPeriodEnd(LocalDate.now().withDayOfMonth(28));
        draftInvoice.setTotalCents(1000L);
        draftInvoice = invoiceRepository.save(draftInvoice);

        AttemptPaymentRequest request = new AttemptPaymentRequest(draftInvoice.getId(), "idem-key-draft");

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, userHeaders(tenant.getId())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
    }

    @Test
    void getLedger_givenMixedEntries_thenBalanceIsCorrect() {
        HttpHeaders headers = userHeaders(tenant.getId());
        rest.exchange("/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(new AttemptPaymentRequest(finalizedInvoice.getId(), "idem-key-mixed"), headers),
                PaymentResponse.class);

        LedgerEntry credit = new LedgerEntry();
        credit.setTenantId(tenant.getId());
        credit.setType(LedgerEntryType.CREDIT);
        credit.setAmountCents(-500L);
        credit.setDescription("Promotional credit");
        credit.setReferenceId(finalizedInvoice.getId());
        ledgerEntryRepository.save(credit);

        ResponseEntity<LedgerSummaryResponse> ledger = rest.exchange(
                "/api/v1/ledger", HttpMethod.GET,
                new HttpEntity<>(headers), LedgerSummaryResponse.class);

        assertThat(ledger.getStatusCode()).isEqualTo(HttpStatus.OK);
        LedgerSummaryResponse body = ledger.getBody();
        assertThat(body).isNotNull();
        assertThat(body.entries()).hasSize(3);
        assertThat(body.balanceCents()).isEqualTo(-500L);
    }
}
