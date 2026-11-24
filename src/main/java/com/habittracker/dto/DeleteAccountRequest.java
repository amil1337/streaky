package com.habittracker.dto;

import jakarta.validation.constraints.*;

public record DeleteAccountRequest(@NotBlank @Size(max = 72) String currentPassword) {}
