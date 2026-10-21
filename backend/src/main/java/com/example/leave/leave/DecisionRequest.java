package com.example.leave.leave;

import jakarta.validation.constraints.Size;

public record DecisionRequest(@Size(max = 500, message = "Comment must be at most 500 characters") String comment) {
}
