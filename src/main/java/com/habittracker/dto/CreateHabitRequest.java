package com.habittracker.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.habittracker.model.HabitType;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateHabitRequest(
    @NotBlank @Size(max = 60) String name,
    @Size(max = 240) String description,
    HabitType kind,
    @JsonAlias("dailyTarget") @Min(1) @Max(999) Integer target,
    @Size(max = 20) String unit,
    @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "Color must be a six-digit hex value")
        String color,
    Boolean defaultHabit,
    @Size(min = 1, max = 7) List<@Min(1) @Max(7) Integer> trackingDays) {
  public CreateHabitRequest(
      String name,
      String description,
      HabitType kind,
      Integer target,
      String unit) {
    this(name, description, kind, target, unit, null, null, null);
  }
}
