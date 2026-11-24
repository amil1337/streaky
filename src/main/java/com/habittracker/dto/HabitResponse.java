package com.habittracker.dto;

import com.habittracker.model.*;
import java.util.*;

public record HabitResponse(
    UUID id,
    String name,
    String description,
    HabitType kind,
    int target,
    String unit,
    String color,
    boolean defaultHabit,
    List<Integer> trackingDays,
    int weeklyGoal,
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
        h.getColor(),
        h.isDefaultHabit(),
        h.getTrackingDays(),
        h.getWeeklyGoal(),
        count,
        Math.min(100.0, count * 100.0 / h.getDailyTarget()),
        count >= h.getDailyTarget());
  }
}
