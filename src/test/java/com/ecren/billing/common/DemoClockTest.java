package com.ecren.billing.common;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoClockTest {

    @Test
    void today_givenNoAdvance_thenMatchesRealDate() {
        DemoClock clock = new DemoClock();
        assertThat(clock.today()).isEqualTo(LocalDate.now());
    }

    @Test
    void advance_givenPositiveDays_thenMovesTodayForward() {
        DemoClock clock = new DemoClock();
        LocalDate result = clock.advance(15);
        assertThat(result).isEqualTo(LocalDate.now().plusDays(15));
        assertThat(clock.today()).isEqualTo(LocalDate.now().plusDays(15));
    }

    @Test
    void advance_calledTwice_thenAccumulates() {
        DemoClock clock = new DemoClock();
        clock.advance(15);
        clock.advance(15);
        assertThat(clock.today()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void advance_givenZeroOrNegativeDays_thenThrows() {
        DemoClock clock = new DemoClock();
        assertThatThrownBy(() -> clock.advance(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clock.advance(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reset_thenReturnsToRealTime() {
        DemoClock clock = new DemoClock();
        clock.advance(100);
        clock.reset();
        assertThat(clock.today()).isEqualTo(LocalDate.now());
    }
}
