package com.ecren.billing.web;

import com.ecren.billing.dto.response.PlanResponse;
import com.ecren.billing.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "2. Plans")
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @Operation(summary = "List plans")
    @GetMapping
    public List<PlanResponse> listPlans() {
        return service.findAll();
    }

    @Operation(summary = "Get plan by ID")
    @GetMapping("/{id}")
    public PlanResponse getPlan(@PathVariable UUID id) {
        return service.findById(id);
    }
}
