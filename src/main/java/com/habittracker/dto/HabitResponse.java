package com.habittracker.dto;

import com.habittracker.model.*;
import java.util.UUID;

public record HabitResponse(
    UUID id,
    String name,
    String description,
    HabitType kind,
    int target,
    String unit,
    int todayCount,
    double percentage,
    boolean completed) {
  public static HabitResponse from(Habit h, int count) {
    return new HabitResponse(
        h.getId(),
        h.getName(),
        h.getDescription(),
        h.getKind(),
        h.getDailyTarget(),
        h.getUnit(),
        count,
        Math.min(100.0, count * 100.0 / h.getDailyTarget()),
        count >= h.getDailyTarget());
  }
}
