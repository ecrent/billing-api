package com.ecren.billing.web;

import com.ecren.billing.common.DemoClock;
import com.ecren.billing.dto.response.DemoTimeResponse;
import com.ecren.billing.dto.response.TimeAdvanceResponse;
import com.ecren.billing.service.BillingCycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Lets the showcase fast-forward the simulated clock instead of waiting for real
 * days to pass, so subscription renewals and deferred plan downgrades can be
 * demoed on demand. The clock only ever moves forward.
 */
@Tag(name = "8. Demo Time")
@RestController
@RequestMapping("/api/v1/time")
public class TimeController {

    private final DemoClock clock;
    private final BillingCycleService billingCycleService;

    public TimeController(DemoClock clock, BillingCycleService billingCycleService) {
        this.clock = clock;
        this.billingCycleService = billingCycleService;
    }

    @Operation(summary = "Get the current simulated date")
    @GetMapping
    public DemoTimeResponse current() {
        return new DemoTimeResponse(clock.today());
    }

    @Operation(summary = "Fast-forward the simulated clock and run any billing cycles that are now due")
    @PostMapping("/advance")
    public TimeAdvanceResponse advance(@RequestParam(defaultValue = "15") long days) {
        LocalDate today = clock.advance(days);
        int cyclesProcessed = billingCycleService.runDueCycles(today);
        String message = "Advanced " + days + " day" + (days == 1 ? "" : "s") + " to " + today
                + (cyclesProcessed > 0
                    ? ". Processed " + cyclesProcessed + " billing cycle" + (cyclesProcessed == 1 ? "" : "s") + "."
                    : ".");
        return new TimeAdvanceResponse(today, cyclesProcessed, message);
    }
}
