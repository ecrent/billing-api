package com.ecren.billing.domain;

import com.ecren.billing.domain.enums.UsageMetric;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "plan_metric_limits")
public class PlanMetricLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsageMetric metric;

    @Column(nullable = false)
    private long includedQuantity;

    @Column(nullable = false)
    private long overagePricePerUnitCents;

    public UUID getId() { return id; }

    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }

    public UsageMetric getMetric() { return metric; }
    public void setMetric(UsageMetric metric) { this.metric = metric; }

    public long getIncludedQuantity() { return includedQuantity; }
    public void setIncludedQuantity(long includedQuantity) { this.includedQuantity = includedQuantity; }

    public long getOveragePricePerUnitCents() { return overagePricePerUnitCents; }
    public void setOveragePricePerUnitCents(long overagePricePerUnitCents) { this.overagePricePerUnitCents = overagePricePerUnitCents; }
}
