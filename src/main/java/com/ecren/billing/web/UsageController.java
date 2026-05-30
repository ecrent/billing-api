package com.ecren.billing.web;

import com.ecren.billing.dto.request.ReportUsageRequest;
import com.ecren.billing.dto.response.UsageRecordResponse;
import com.ecren.billing.dto.response.UsageSummaryResponse;
import com.ecren.billing.service.UsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "4. Usage")
@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageService service;

    public UsageController(UsageService service) {
        this.service = service;
    }

    @Operation(summary = "Report usage")
    @PostMapping
    public ResponseEntity<UsageRecordResponse> report(@Valid @RequestBody ReportUsageRequest request) {
        UsageService.ReportResult result = service.report(request);
        if (result.isNew()) {
            return ResponseEntity.status(201).body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @Operation(summary = "Get usage summary")
    @GetMapping("/summary")
    public UsageSummaryResponse getSummary() {
        return service.getSummary();
    }
}
