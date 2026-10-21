package com.example.leave.leave;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "A comment is required when rejecting a request")
        @Size(max = 500, message = "Comment must be at most 500 characters")
        String comment) {
}
