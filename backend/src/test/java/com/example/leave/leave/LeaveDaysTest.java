package com.example.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LeaveDaysTest {

    private static final LocalDate JAN_1 = LocalDate.of(2030, 1, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2030, 12, 31);

    @Test
    void countsCalendarDaysInclusively() {
        assertThat(LeaveDays.between(JAN_1, JAN_1)).isEqualTo(1);
        assertThat(LeaveDays.between(JAN_1, JAN_1.plusDays(6))).isEqualTo(7);
    }

    @Test
    void returnsZeroWhenEndIsBeforeStart() {
        assertThat(LeaveDays.between(JAN_1.plusDays(1), JAN_1)).isZero();
    }

    @Test
    void clipsToRange() {
        assertThat(LeaveDays.withinRange(JAN_1.minusDays(3), JAN_1.plusDays(1), JAN_1, DEC_31)).isEqualTo(2);
        assertThat(LeaveDays.withinRange(DEC_31.minusDays(1), DEC_31.plusDays(5), JAN_1, DEC_31)).isEqualTo(2);
        assertThat(LeaveDays.withinRange(JAN_1.minusDays(10), JAN_1.minusDays(5), JAN_1, DEC_31)).isZero();
    }
}
