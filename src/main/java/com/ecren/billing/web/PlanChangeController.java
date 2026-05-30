package com.ecren.billing.web;

import com.ecren.billing.dto.request.ChangePlanRequest;
import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.service.PlanChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "3. Subscriptions")
@RestController
@RequestMapping("/api/v1/subscriptions/current")
public class PlanChangeController {

    private final PlanChangeService service;

    public PlanChangeController(PlanChangeService service) {
        this.service = service;
    }

    @Operation(summary = "Change plan with proration")
    @PostMapping("/change-plan")
    public ResponseEntity<InvoiceResponse> changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return service.changePlan(request);
    }
}
