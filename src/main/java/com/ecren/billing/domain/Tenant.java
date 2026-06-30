package com.ecren.billing.domain;

import com.ecren.billing.common.BaseEntity;
import com.ecren.billing.domain.enums.TenantStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "tenants")
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    // Demo play money. Every tenant starts with $1,000; recurring charges draw it
    // down and the subscription drops once it can no longer cover a charge.
    @Column(name = "wallet_balance_cents", nullable = false)
    private long walletBalanceCents = 100_000L;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public TenantStatus getStatus() { return status; }
    public void setStatus(TenantStatus status) { this.status = status; }

    public long getWalletBalanceCents() { return walletBalanceCents; }
    public void setWalletBalanceCents(long walletBalanceCents) { this.walletBalanceCents = walletBalanceCents; }
}
