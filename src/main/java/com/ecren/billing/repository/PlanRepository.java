package com.ecren.billing.repository;

import com.ecren.billing.domain.Plan;
import com.ecren.billing.domain.enums.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findByStatus(PlanStatus status);
}
