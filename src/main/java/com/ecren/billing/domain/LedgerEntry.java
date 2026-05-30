package com.ecren.billing.domain;

import com.ecren.billing.domain.enums.LedgerEntryType;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column
    private LedgerEntryType type;

    @Column
    private long amountCents;

    @Column
    private String description;

    @Column
    private UUID referenceId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public LedgerEntryType getType() { return type; }
    public void setType(LedgerEntryType type) { this.type = type; }

    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
