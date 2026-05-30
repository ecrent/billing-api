package com.ecren.billing.web;

import com.ecren.billing.dto.request.ReportUsageRequest;
import com.ecren.billing.dto.response.UsageRecordResponse;
import com.ecren.billing.dto.response.UsageSummaryResponse;
import com.ecren.billing.service.UsageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageService service;

    public UsageController(UsageService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UsageRecordResponse> report(@Valid @RequestBody ReportUsageRequest request) {
        UsageService.ReportResult result = service.report(request);
        if (result.isNew()) {
            return ResponseEntity.status(201).body(result.response());
        }
        return ResponseEntity.ok(result.response());
    }

    @GetMapping("/summary")
    public UsageSummaryResponse getSummary() {
        return service.getSummary();
    }
}
