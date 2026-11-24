package com.habittracker.repository;

import com.habittracker.model.HabitProgress;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HabitProgressRepository extends JpaRepository<HabitProgress, UUID> {
  Optional<HabitProgress> findByHabit_IdAndDate(UUID habitId, LocalDate date);

  List<HabitProgress> findAllByHabit_IdAndDateBetweenOrderByDateAsc(
      UUID habitId, LocalDate start, LocalDate end);

  @Query("select p from HabitProgress p where p.habit.user.id = :userId and p.date = :date")
  List<HabitProgress> findOwnedOnDate(UUID userId, LocalDate date);

  @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "habit")
  List<HabitProgress> findAllByHabit_User_Id(UUID userId);

  boolean existsByHabit_IdAndCountGreaterThan(UUID habitId, int count);
}
