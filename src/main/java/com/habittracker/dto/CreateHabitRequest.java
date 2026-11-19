package com.habittracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.habittracker.model.HabitType;
import jakarta.validation.constraints.*;

public record CreateHabitRequest(
    @NotBlank @Size(max = 60) String name,
    @Size(max = 240) String description,
    HabitType kind,
    @JsonAlias("dailyTarget") @Min(1) @Max(999) Integer target,
    @Size(max = 20) String unit) {}
