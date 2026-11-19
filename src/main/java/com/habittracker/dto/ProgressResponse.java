package com.habittracker.dto;

import com.habittracker.model.HabitProgress;
import java.time.LocalDate;

public record ProgressResponse(LocalDate date, int count, int dailyTarget) {

  public static ProgressResponse from(HabitProgress progress) {

    return new ProgressResponse(
        progress.getDate(), progress.getCount(), progress.getHabit().getDailyTarget());
  }
}
