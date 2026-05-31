package com.ecren.billing.web;

import com.ecren.billing.TestcontainersConfiguration;
import com.ecren.billing.domain.Invoice;
import com.ecren.billing.domain.InvoiceLineItem;
import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.Subscription;
import com.ecren.billing.domain.Tenant;
import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LineItemType;
import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.domain.enums.TenantStatus;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.dto.response.PageResponse;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceControllerIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    InvoiceRepository invoiceRepository;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    PlanRepository planRepository;

    @Autowired
    TenantRepository tenantRepository;

    private Tenant tenant;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        invoiceRepository.deleteAll();
        subscriptionRepository.deleteAll();
        planRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = new Tenant();
        tenant.setName("Invoice Corp");
        tenant.setEmail("invoices@example.com");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);

        Plan plan = new Plan();
        plan.setName("Basic");
        plan.setSlug("basic-invoice");
        plan.setBasePriceCents(999L);
        plan.setBillingInterval("MONTHLY");
        plan.setStatus(PlanStatus.ACTIVE);
        plan = planRepository.save(plan);

        subscription = new Subscription();
        subscription.setTenantId(tenant.getId());
        subscription.setPlanId(plan.getId());
        subscription.setCurrentPeriodStart(LocalDate.now());
        subscription.setCurrentPeriodEnd(LocalDate.now().plusDays(30));
        subscription = subscriptionRepository.save(subscription);
    }

    @Test
    void listInvoices_givenInvoicesExist_thenReturnsPaginatedList() {
        invoiceRepository.save(buildInvoice(InvoiceStatus.DRAFT));
        invoiceRepository.save(buildInvoice(InvoiceStatus.FINALIZED));

        ResponseEntity<PageResponse<Map<String, Object>>> response = rest.exchange(
                "/api/v1/invoices?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(headersWithTenantId(tenant.getId())),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<Map<String, Object>> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.content()).hasSize(2);
        assertThat(body.page()).isEqualTo(0);
        assertThat(body.size()).isEqualTo(20);
        assertThat(body.totalElements()).isEqualTo(2);
        assertThat(body.totalPages()).isEqualTo(1);
    }

    @Test
    void getInvoice_givenExistingId_thenReturnsInvoiceWithLineItems() {
        Invoice invoice = buildInvoice(InvoiceStatus.DRAFT);
        InvoiceLineItem item1 = buildLineItem(invoice, LineItemType.BASE_FEE, "Base fee", 999L);
        InvoiceLineItem item2 = buildLineItem(invoice, LineItemType.USAGE_OVERAGE, "Usage overage", 200L);
        invoice.getLineItems().add(item1);
        invoice.getLineItems().add(item2);
        invoice = invoiceRepository.save(invoice);

        ResponseEntity<InvoiceResponse> response = rest.exchange(
                "/api/v1/invoices/" + invoice.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headersWithTenantId(tenant.getId())),
                InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InvoiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.invoiceId()).isEqualTo(invoice.getId());
        assertThat(body.lineItems()).hasSize(2);
    }

    @Test
    void getInvoice_givenDifferentTenantInvoice_thenReturns404() {
        Invoice invoice = buildInvoice(InvoiceStatus.DRAFT);
        invoice = invoiceRepository.save(invoice);

        UUID otherTenantId = UUID.randomUUID();

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/invoices/" + invoice.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headersWithTenantId(otherTenantId)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void voidInvoice_givenDraftInvoice_thenReturnsVoid() {
        Invoice invoice = invoiceRepository.save(buildInvoice(InvoiceStatus.DRAFT));

        ResponseEntity<InvoiceResponse> response = rest.exchange(
                "/api/v1/invoices/" + invoice.getId() + "/void",
                HttpMethod.POST,
                new HttpEntity<>(headersWithTenantId(tenant.getId())),
                InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InvoiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("VOID");
    }

    @Test
    void voidInvoice_givenFinalizedInvoice_thenReturnsVoid() {
        Invoice invoice = invoiceRepository.save(buildInvoice(InvoiceStatus.FINALIZED));

        ResponseEntity<InvoiceResponse> response = rest.exchange(
                "/api/v1/invoices/" + invoice.getId() + "/void",
                HttpMethod.POST,
                new HttpEntity<>(headersWithTenantId(tenant.getId())),
                InvoiceResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        InvoiceResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo("VOID");
    }

    @Test
    void voidInvoice_givenPaidInvoice_thenReturns409() {
        Invoice invoice = invoiceRepository.save(buildInvoice(InvoiceStatus.PAID));

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/invoices/" + invoice.getId() + "/void",
                HttpMethod.POST,
                new HttpEntity<>(headersWithTenantId(tenant.getId())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    private HttpHeaders headersWithTenantId(UUID tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantId.toString());
        return headers;
    }

    private Invoice buildInvoice(InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setTenantId(tenant.getId());
        invoice.setSubscriptionId(subscription.getId());
        invoice.setStatus(status);
        invoice.setPeriodStart(LocalDate.now().withDayOfMonth(1));
        invoice.setPeriodEnd(LocalDate.now().withDayOfMonth(28));
        invoice.setTotalCents(999L);
        return invoice;
    }

    private InvoiceLineItem buildLineItem(Invoice invoice, LineItemType type, String description, long unitPriceCents) {
        InvoiceLineItem item = new InvoiceLineItem();
        item.setInvoice(invoice);
        item.setType(type);
        item.setDescription(description);
        item.setQuantity(1L);
        item.setUnitPriceCents(unitPriceCents);
        item.setAmountCents(unitPriceCents);
        return item;
    }
}
