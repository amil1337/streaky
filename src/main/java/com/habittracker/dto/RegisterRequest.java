package com.habittracker.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank
        @Pattern(
            regexp = "[A-Za-z0-9_.-]{3,40}",
            message = "Username must be 3–40 letters, numbers, dots, underscores or hyphens")
        String username,
    @NotBlank @Size(min = 8, max = 72) String password,
    @Size(max = 64) String timezone) {}
