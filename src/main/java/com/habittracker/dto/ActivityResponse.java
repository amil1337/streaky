package com.habittracker.dto;

import com.habittracker.model.ActivityEvent;
import java.time.*;
import java.util.UUID;

public record ActivityResponse(
    Long id,
    UUID habitId,
    String name,
    String action,
    int count,
    String unit,
    LocalDate date,
    Instant createdAt) {
  public static ActivityResponse from(ActivityEvent e) {
    return new ActivityResponse(
        e.getId(),
        e.getHabitId(),
        e.getName(),
        e.getAction(),
        e.getCount(),
        e.getUnit(),
        e.getDate(),
        e.getCreatedAt());
  }
}
