package com.habittracker.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
    @Size(min = 1, max = 40) String name,
    @Pattern(regexp = "[A-Za-z0-9_.-]{3,40}",
        message = "Username must be 3–40 letters, numbers, dots, underscores or hyphens")
        String username,
    @Size(max = 240) String bio,
    @Size(max = 64) String timezone) {}
