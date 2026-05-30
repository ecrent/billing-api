package com.ecren.billing.web;

import com.ecren.billing.dto.request.ChangePlanRequest;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.service.PlanChangeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions/current")
public class PlanChangeController {

    private final PlanChangeService service;

    public PlanChangeController(PlanChangeService service) {
        this.service = service;
    }

    @PostMapping("/change-plan")
    public ResponseEntity<InvoiceResponse> changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return service.changePlan(request);
    }
}
