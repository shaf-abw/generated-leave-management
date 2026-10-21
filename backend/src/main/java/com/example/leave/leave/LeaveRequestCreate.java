package com.example.leave.leave;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record LeaveRequestCreate(
        @NotNull(message = "Type is required") LeaveType type,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {
}
