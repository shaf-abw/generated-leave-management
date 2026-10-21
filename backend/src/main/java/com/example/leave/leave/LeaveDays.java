package com.example.leave.leave;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Leave duration is counted in calendar days, inclusive of both start and end date. */
public final class LeaveDays {

    private LeaveDays() {
    }

    public static long between(LocalDate start, LocalDate end) {
        return end.isBefore(start) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
    }

    public static long withinRange(LocalDate start, LocalDate end, LocalDate rangeStart, LocalDate rangeEnd) {
        LocalDate clippedStart = start.isBefore(rangeStart) ? rangeStart : start;
        LocalDate clippedEnd = end.isAfter(rangeEnd) ? rangeEnd : end;
        return between(clippedStart, clippedEnd);
    }
}
