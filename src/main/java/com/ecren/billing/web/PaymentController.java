package com.ecren.billing.web;

import com.ecren.billing.dto.request.AttemptPaymentRequest;
import com.ecren.billing.dto.response.PaymentResponse;
import com.ecren.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@Tag(name = "6. Payments")
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
        return ResponseEntity.created(URI.create("/api/v1/payments/" + result.response().paymentId())).body(result.response());
    }

    @Operation(summary = "Get payment by ID")
    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(
            @Parameter(example = "00000000-0000-0000-0000-000000001000")
            @PathVariable UUID paymentId) {
        return service.getPayment(paymentId);
    }
}
