package com.ecren.billing.web;

import com.ecren.billing.dto.response.PlanResponse;
import com.ecren.billing.service.PlanService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @GetMapping
    public List<PlanResponse> listPlans() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public PlanResponse getPlan(@PathVariable UUID id) {
        return service.findById(id);
    }
}
