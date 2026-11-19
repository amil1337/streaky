package com.habittracker.repository;

import com.habittracker.model.HabitProgress;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabitProgressRepository extends JpaRepository<HabitProgress, UUID> {
  Optional<HabitProgress> findByHabit_IdAndDate(UUID habitId, LocalDate date);

  List<HabitProgress> findAllByHabit_IdAndDateBetweenOrderByDateAsc(
      UUID habitId, LocalDate start, LocalDate end);

  boolean existsByHabit_IdAndCountGreaterThan(UUID habitId, int count);
}
