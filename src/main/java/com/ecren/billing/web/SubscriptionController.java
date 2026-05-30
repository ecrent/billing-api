package com.ecren.billing.web;

import com.ecren.billing.dto.request.CreateSubscriptionRequest;
import com.ecren.billing.dto.response.SubscriptionResponse;
import com.ecren.billing.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @Operation(summary = "Subscribe to plan")
    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(
            @Valid @RequestBody CreateSubscriptionRequest request,
            UriComponentsBuilder uriBuilder) {
        SubscriptionResponse response = service.subscribe(request);
        var location = uriBuilder.path("/api/v1/subscriptions/current").build().toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Operation(summary = "Get current subscription")
    @GetMapping("/current")
    public SubscriptionResponse getCurrent() {
        return service.getCurrent();
    }

    @Operation(summary = "Cancel subscription")
    @DeleteMapping("/current")
    public SubscriptionResponse cancel() {
        return service.cancel();
    }
}
