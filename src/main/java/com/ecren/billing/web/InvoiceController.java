package com.ecren.billing.web;

import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.dto.response.PageResponse;
import com.ecren.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "5. Invoices")
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @Operation(summary = "List invoices")
    @GetMapping
    public PageResponse<InvoiceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return service.getAll(page, size, status);
    }

    @Operation(summary = "Get invoice by ID")
    @GetMapping("/{invoiceId}")
    public InvoiceResponse getById(
            @Parameter(example = "00000000-0000-0000-0000-000000000100")
            @PathVariable UUID invoiceId) {
        return service.getById(invoiceId);
    }

    @Operation(summary = "Void invoice")
    @PostMapping("/{invoiceId}/void")
    public InvoiceResponse void_(
            @Parameter(example = "00000000-0000-0000-0000-000000000100")
            @PathVariable UUID invoiceId) {
        return service.void_(invoiceId);
    }
}
