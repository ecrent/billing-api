package com.ecren.billing.web;

import com.ecren.billing.dto.request.AttemptPaymentRequest;
import com.ecren.billing.dto.response.PaymentResponse;
import com.ecren.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @Operation(summary = "Attempt payment")
    @PostMapping
    public ResponseEntity<PaymentResponse> attemptPayment(@Valid @RequestBody AttemptPaymentRequest request) {
        PaymentService.PaymentResult result = service.attemptPayment(request);
        if (!result.isNew()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create("/api/v1/payments/" + result.response().id())).body(result.response());
    }

    @Operation(summary = "Get payment by ID")
    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        return service.getPayment(id);
    }
}
