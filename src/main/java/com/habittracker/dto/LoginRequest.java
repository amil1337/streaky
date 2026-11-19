package com.habittracker.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Size(max = 40) String username, @NotBlank @Size(max = 72) String password) {}
