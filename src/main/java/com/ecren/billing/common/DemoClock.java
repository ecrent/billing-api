package com.ecren.billing.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory simulated clock for the showcase: lets a user fast-forward time
 * so billing cycles (renewals, deferred downgrades) can be demoed without
 * waiting for real days to pass. The offset only ever moves forward and
 * resets to real time when the app restarts — there is no "rewind".
 */
@Component
public class DemoClock {

    private final AtomicLong advancedDays = new AtomicLong(0);

    public LocalDate today() {
        return LocalDate.now().plusDays(advancedDays.get());
    }

    public LocalDateTime now() {
        return LocalDateTime.now().plusDays(advancedDays.get());
    }

    /** Moves the simulated clock forward by the given number of days and returns the new "today". */
    public LocalDate advance(long days) {
        if (days <= 0) {
            throw new IllegalArgumentException("days must be positive — the demo clock only moves forward");
        }
        advancedDays.addAndGet(days);
        return today();
    }

    /** Test-only: resets the simulated offset back to real time. */
    public void reset() {
        advancedDays.set(0);
    }
}
