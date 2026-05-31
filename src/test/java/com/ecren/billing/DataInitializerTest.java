package com.ecren.billing;

import com.ecren.billing.domain.enums.InvoiceStatus;
import com.ecren.billing.domain.enums.LedgerEntryType;
import com.ecren.billing.domain.enums.PaymentStatus;
import com.ecren.billing.domain.enums.SubscriptionStatus;
import com.ecren.billing.repository.InvoiceRepository;
import com.ecren.billing.repository.LedgerEntryRepository;
import com.ecren.billing.repository.PaymentRepository;
import com.ecren.billing.repository.PlanRepository;
import com.ecren.billing.repository.SubscriptionRepository;
import com.ecren.billing.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class DataInitializerTest {

    static final UUID ALICE_TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID BOB_TENANT_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID CAROL_TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final UUID ALICE_SUB_ID     = UUID.fromString("00000000-0000-0000-0000-000000000011");
    static final UUID BOB_SUB_ID       = UUID.fromString("00000000-0000-0000-0000-000000000022");
    static final UUID CAROL_SUB_ID     = UUID.fromString("00000000-0000-0000-0000-000000000033");

    static final UUID ALICE_INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    static final UUID BOB_INVOICE_ID   = UUID.fromString("00000000-0000-0000-0000-000000000200");
    static final UUID CAROL_INVOICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000300");

    static final UUID ALICE_PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000001000");
    static final UUID BOB_PAYMENT_ID   = UUID.fromString("00000000-0000-0000-0000-000000002000");
    static final UUID CAROL_PAYMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000003000");

    @Autowired TenantRepository tenantRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
    @Autowired PlanRepository planRepository;

    @Test
    void allThreeTenantsExistWithStableUUIDs() {
        assertThat(tenantRepository.findById(ALICE_TENANT_ID)).isPresent();
        assertThat(tenantRepository.findById(BOB_TENANT_ID)).isPresent();
        assertThat(tenantRepository.findById(CAROL_TENANT_ID)).isPresent();
    }

    @Test
    void tenantDetailsAreCorrect() {
        var alice = tenantRepository.findById(ALICE_TENANT_ID).orElseThrow();
        assertThat(alice.getName()).isEqualTo("Alice Johnson");
        assertThat(alice.getEmail()).isEqualTo("alice@example.com");

        var bob = tenantRepository.findById(BOB_TENANT_ID).orElseThrow();
        assertThat(bob.getName()).isEqualTo("Bob Smith");
        assertThat(bob.getEmail()).isEqualTo("bob@example.com");

        var carol = tenantRepository.findById(CAROL_TENANT_ID).orElseThrow();
        assertThat(carol.getName()).isEqualTo("Carol Davis");
        assertThat(carol.getEmail()).isEqualTo("carol@example.com");
    }

    @Test
    void subscriptionsExistForEachTenantWithStableUUIDs() {
        assertThat(subscriptionRepository.findById(ALICE_SUB_ID)).isPresent();
        assertThat(subscriptionRepository.findById(BOB_SUB_ID)).isPresent();
        assertThat(subscriptionRepository.findById(CAROL_SUB_ID)).isPresent();
    }

    @Test
    void subscriptionsAreActiveAndLinkedToRealPlans() {
        var plans = planRepository.findAll();
        var planIds = plans.stream().map(p -> p.getId()).toList();

        var aliceSub = subscriptionRepository.findById(ALICE_SUB_ID).orElseThrow();
        assertThat(aliceSub.getTenantId()).isEqualTo(ALICE_TENANT_ID);
        assertThat(aliceSub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(planIds).contains(aliceSub.getPlanId());

        var bobSub = subscriptionRepository.findById(BOB_SUB_ID).orElseThrow();
        assertThat(bobSub.getTenantId()).isEqualTo(BOB_TENANT_ID);
        assertThat(bobSub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(planIds).contains(bobSub.getPlanId());

        var carolSub = subscriptionRepository.findById(CAROL_SUB_ID).orElseThrow();
        assertThat(carolSub.getTenantId()).isEqualTo(CAROL_TENANT_ID);
        assertThat(carolSub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(planIds).contains(carolSub.getPlanId());
    }

    @Test
    void invoicesExistAndArePaidWithCorrectAmounts() {
        var aliceInvoice = invoiceRepository.findById(ALICE_INVOICE_ID).orElseThrow();
        assertThat(aliceInvoice.getTenantId()).isEqualTo(ALICE_TENANT_ID);
        assertThat(aliceInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(aliceInvoice.getTotalCents()).isEqualTo(900L);
        assertThat(aliceInvoice.getLineItems()).hasSize(1);

        var bobInvoice = invoiceRepository.findById(BOB_INVOICE_ID).orElseThrow();
        assertThat(bobInvoice.getTenantId()).isEqualTo(BOB_TENANT_ID);
        assertThat(bobInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(bobInvoice.getTotalCents()).isEqualTo(2900L);
        assertThat(bobInvoice.getLineItems()).hasSize(1);

        var carolInvoice = invoiceRepository.findById(CAROL_INVOICE_ID).orElseThrow();
        assertThat(carolInvoice.getTenantId()).isEqualTo(CAROL_TENANT_ID);
        assertThat(carolInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(carolInvoice.getTotalCents()).isEqualTo(9900L);
        assertThat(carolInvoice.getLineItems()).hasSize(1);
    }

    @Test
    void paymentsExistAndAreSucceeded() {
        var alicePayment = paymentRepository.findById(ALICE_PAYMENT_ID).orElseThrow();
        assertThat(alicePayment.getTenantId()).isEqualTo(ALICE_TENANT_ID);
        assertThat(alicePayment.getInvoiceId()).isEqualTo(ALICE_INVOICE_ID);
        assertThat(alicePayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(alicePayment.getAmountCents()).isEqualTo(900L);

        var bobPayment = paymentRepository.findById(BOB_PAYMENT_ID).orElseThrow();
        assertThat(bobPayment.getTenantId()).isEqualTo(BOB_TENANT_ID);
        assertThat(bobPayment.getInvoiceId()).isEqualTo(BOB_INVOICE_ID);
        assertThat(bobPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(bobPayment.getAmountCents()).isEqualTo(2900L);

        var carolPayment = paymentRepository.findById(CAROL_PAYMENT_ID).orElseThrow();
        assertThat(carolPayment.getTenantId()).isEqualTo(CAROL_TENANT_ID);
        assertThat(carolPayment.getInvoiceId()).isEqualTo(CAROL_INVOICE_ID);
        assertThat(carolPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(carolPayment.getAmountCents()).isEqualTo(9900L);
    }

    @Test
    void eachTenantHasTwoLedgerEntries_chargeAndPayment() {
        var aliceEntries = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(ALICE_TENANT_ID);
        assertThat(aliceEntries).hasSize(2);
        assertThat(aliceEntries).anyMatch(e -> e.getType() == LedgerEntryType.CHARGE);
        assertThat(aliceEntries).anyMatch(e -> e.getType() == LedgerEntryType.PAYMENT);

        var bobEntries = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(BOB_TENANT_ID);
        assertThat(bobEntries).hasSize(2);
        assertThat(bobEntries).anyMatch(e -> e.getType() == LedgerEntryType.CHARGE);
        assertThat(bobEntries).anyMatch(e -> e.getType() == LedgerEntryType.PAYMENT);

        var carolEntries = ledgerEntryRepository.findByTenantIdOrderByCreatedAtDesc(CAROL_TENANT_ID);
        assertThat(carolEntries).hasSize(2);
        assertThat(carolEntries).anyMatch(e -> e.getType() == LedgerEntryType.CHARGE);
        assertThat(carolEntries).anyMatch(e -> e.getType() == LedgerEntryType.PAYMENT);
    }
}
