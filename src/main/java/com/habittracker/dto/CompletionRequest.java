package com.habittracker.dto;

import jakarta.validation.constraints.NotNull;

public record CompletionRequest(@NotNull Boolean completed) {}
