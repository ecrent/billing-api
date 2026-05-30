package com.ecren.billing.web;

import com.ecren.billing.dto.response.InvoiceResponse;
import com.ecren.billing.dto.response.PageResponse;
import com.ecren.billing.service.InvoiceService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InvoiceResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return service.getAll(page, size, status);
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/void")
    public InvoiceResponse void_(@PathVariable UUID id) {
        return service.void_(id);
    }
}
