package com.ecren.billing.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Central factory for building {@link Pageable} objects from client-supplied
 * request parameters. Clamps values so a single anonymous request cannot force
 * the server to materialize an unbounded result set (OOM / DoS vector on the
 * memory-limited container).
 */
public final class PageRequests {

    /** Upper bound applied to any client-supplied page size. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final int MIN_PAGE_SIZE = 1;

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, MIN_PAGE_SIZE), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize);
    }
}
