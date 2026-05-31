package com.ecren.billing.repository;

import com.ecren.billing.domain.Payment;
import com.ecren.billing.domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIdempotencyKeyAndTenantId(String key, UUID tenantId);
    long countByInvoiceIdAndStatus(UUID invoiceId, PaymentStatus status);
}
