package com.ecren.billing.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class PageRequestsTest {

    @Test
    void of_givenOversizedSize_thenClampsToMax() {
        Pageable pageable = PageRequests.of(0, 1_000_000);

        assertThat(pageable.getPageSize()).isEqualTo(PageRequests.MAX_PAGE_SIZE);
        assertThat(pageable.getPageSize()).isLessThanOrEqualTo(100);
    }

    @Test
    void of_givenNegativeOrZeroSize_thenClampsToMinimum() {
        assertThat(PageRequests.of(0, 0).getPageSize()).isEqualTo(1);
        assertThat(PageRequests.of(0, -50).getPageSize()).isEqualTo(1);
    }

    @Test
    void of_givenNegativePage_thenClampsToZero() {
        assertThat(PageRequests.of(-1, 20).getPageNumber()).isZero();
        assertThat(PageRequests.of(-100, 20).getPageNumber()).isZero();
    }

    @Test
    void of_givenNormalValues_thenPassesThroughUnchanged() {
        Pageable pageable = PageRequests.of(3, 20);

        assertThat(pageable.getPageNumber()).isEqualTo(3);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }
}
