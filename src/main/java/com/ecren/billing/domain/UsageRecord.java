package com.ecren.billing.domain;

import com.ecren.billing.domain.enums.UsageMetric;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
@EntityListeners(AuditingEntityListener.class)
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private UUID tenantId;

    @Column
    private UUID subscriptionId;

    @Enumerated(EnumType.STRING)
    @Column
    private UsageMetric metric;

    @Column
    private long quantity;

    @CreatedDate
    @Column
    private LocalDateTime recordedAt;

    @Column
    private String idempotencyKey;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public UsageMetric getMetric() { return metric; }
    public void setMetric(UsageMetric metric) { this.metric = metric; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
