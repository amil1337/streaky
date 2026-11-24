package com.habittracker.service;

import com.habittracker.repository.ActivityRepository;
import com.habittracker.repository.HabitProgressRepository;
import com.habittracker.repository.HabitRepository;
import com.habittracker.repository.UserRepository;
import com.habittracker.dto.ProfileStatisticsResponse;
import com.habittracker.model.HabitProgress;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final UserRepository users;
  private final HabitRepository habits;
  private final HabitProgressRepository progress;
  private final ActivityRepository events;
  private final AuthService auth;
  private final Clock clock;

  public AccountService(
      UserRepository users,
      HabitRepository habits,
      HabitProgressRepository progress,
      ActivityRepository events,
      AuthService auth,
      Clock clock) {
    this.users = users;
    this.habits = habits;
    this.progress = progress;
    this.events = events;
    this.auth = auth;
    this.clock = clock;
  }

  @Transactional
  public void delete(UUID userId, String currentPassword) {
    var user = auth.lockUser(userId);
    auth.verifyPassword(user, currentPassword);
    // Drain progress writers before deleting the account's activity history.
    var owned = habits.lockAllOwned(userId);
    events.deleteByUserId(userId);
    habits.deleteAll(owned);
    habits.flush();
    users.delete(user);
  }

  @Transactional(readOnly = true)
  public ProfileStatisticsResponse statistics(UUID userId) {
    var user = auth.getUser(userId);
    var allHabits = habits.findAllByUser_IdOrderByDefaultHabitDescNameAsc(userId);
    var entries = progress.findAllByHabit_User_Id(userId);
    Map<UUID, SortedSet<LocalDate>> completed = new HashMap<>();
    for (HabitProgress entry : entries) {
      if (entry.getCount() >= entry.getHabit().getDailyTarget()) {
        completed
            .computeIfAbsent(entry.getHabit().getId(), ignored -> new TreeSet<>())
            .add(entry.getDate());
      }
    }

    int longest = 0;
    int current = 0;
    LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(user.getTimezone())));
    for (SortedSet<LocalDate> dates : completed.values()) {
      int run = 0;
      LocalDate previous = null;
      for (LocalDate date : dates) {
        run = previous != null && date.equals(previous.plusDays(1)) ? run + 1 : 1;
        longest = Math.max(longest, run);
        previous = date;
      }
      LocalDate cursor = dates.contains(today) ? today : today.minusDays(1);
      int active = 0;
      while (dates.contains(cursor)) {
        active++;
        cursor = cursor.minusDays(1);
      }
      current = Math.max(current, active);
    }
    int completedDays = completed.values().stream().mapToInt(Set::size).sum();
    return new ProfileStatisticsResponse(allHabits.size(), completedDays, current, longest);
  }
}
