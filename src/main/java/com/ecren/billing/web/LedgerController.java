package com.ecren.billing.web;

import com.ecren.billing.dto.response.LedgerSummaryResponse;
import com.ecren.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final PaymentService service;

    public LedgerController(PaymentService service) {
        this.service = service;
    }

    @Operation(summary = "Get ledger with balance")
    @GetMapping
    public LedgerSummaryResponse getLedger() {
        return service.getLedger();
    }
}
