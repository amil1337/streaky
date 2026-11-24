package com.habittracker.repository;

import com.habittracker.model.Habit;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface HabitRepository extends JpaRepository<Habit, UUID> {
  List<Habit> findAllByUser_IdOrderByDefaultHabitDescNameAsc(UUID userId);

  boolean existsByUser_IdAndDefaultHabitTrue(UUID userId);

  long countByUser_Id(UUID userId);

  @Modifying(flushAutomatically = true)
  @Query("update Habit h set h.defaultHabit = false where h.user.id = :userId and h.id <> :habitId and h.defaultHabit = true")
  void clearOtherDefaults(UUID userId, UUID habitId);

  Optional<Habit> findByIdAndUser_Id(UUID id, UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from Habit h where h.user.id = :userId order by h.id")
  List<Habit> lockAllOwned(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from Habit h where h.id=:habitId and h.user.id=:userId")
  Optional<Habit> lockOwned(UUID habitId, UUID userId);
}
