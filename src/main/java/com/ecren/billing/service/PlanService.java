package com.ecren.billing.service;

import com.ecren.billing.domain.enums.PlanStatus;
import com.ecren.billing.dto.response.PlanResponse;
import com.ecren.billing.exception.ResourceNotFoundException;
import com.ecren.billing.mapper.PlanMapper;
import com.ecren.billing.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository repository;
    private final PlanMapper mapper;

    public PlanService(PlanRepository repository, PlanMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<PlanResponse> findAll() {
        return repository.findByStatus(PlanStatus.ACTIVE).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public PlanResponse findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + id));
    }
}
