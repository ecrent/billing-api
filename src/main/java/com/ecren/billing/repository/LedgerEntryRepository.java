package com.ecren.billing.repository;

import com.ecren.billing.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
