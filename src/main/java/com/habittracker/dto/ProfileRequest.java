package com.habittracker.dto;

import jakarta.validation.constraints.*;

public record ProfileRequest(
    @NotBlank @Size(max = 40) String name,
    @Size(max = 240) String bio,
    @Size(max = 64) String timezone) {}
