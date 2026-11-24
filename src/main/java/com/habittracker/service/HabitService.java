package com.habittracker.service;

import static org.springframework.http.HttpStatus.*;

import com.habittracker.dto.*;
import com.habittracker.model.*;
import com.habittracker.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class HabitService {
  private final HabitRepository habits;
  private final HabitProgressRepository progress;
  private final AuthService auth;
  private final Clock clock;

  public HabitService(
      HabitRepository habits, HabitProgressRepository progress, AuthService auth, Clock clock) {
    this.habits = habits;
    this.progress = progress;
    this.auth = auth;
    this.clock = clock;
  }

  public HabitResponse create(UUID userId, CreateHabitRequest r) {
    User user = auth.lockUser(userId);
    HabitType type =
        r.kind() != null
            ? r.kind()
            : r.target() != null && r.target() > 1 ? HabitType.COUNTER : HabitType.COMPLETION;
    int target = r.target() == null ? 5 : r.target();
    boolean makeDefault = Boolean.TRUE.equals(r.defaultHabit()) || habits.countByUser_Id(userId) == 0;
    Habit h =
        habits.save(
            new Habit(
                r.name(),
                r.description(),
                type,
                target,
                r.unit(),
                color(r.color(), "#FF3434"),
                makeDefault,
                127,
                user));
    if (makeDefault) habits.clearOtherDefaults(userId, h.getId());
    return HabitResponse.from(h, 0);
  }

  @Transactional(readOnly = true)
  public List<HabitResponse> getUserHabits(UUID userId) {
    List<Habit> owned = habits.findAllByUser_IdOrderByDefaultHabitDescNameAsc(userId);
    if (owned.isEmpty()) return List.of();
    LocalDate day = LocalDate.now(clock.withZone(ZoneId.of(owned.getFirst().getUser().getTimezone())));
    Map<UUID, Integer> counts = new HashMap<>();
    for (HabitProgress entry : progress.findOwnedOnDate(userId, day)) {
      counts.put(entry.getHabit().getId(), entry.getCount());
    }
    return owned.stream().map(h -> HabitResponse.from(h, counts.getOrDefault(h.getId(), 0))).toList();
  }

  public HabitResponse update(UUID userId, UUID habitId, UpdateHabitRequest r) {
    auth.lockUser(userId);
    Habit h = lock(userId, habitId);
    HabitType kind = r.kind() == null ? h.getKind() : r.kind();
    if (kind != h.getKind() && progress.existsByHabit_IdAndCountGreaterThan(habitId, 0))
      throw new ResponseStatusException(CONFLICT, "Tracking type is fixed once activity exists");
    h.update(
        r.name(),
        r.description() == null ? h.getDescription() : r.description(),
        kind,
        r.target() == null ? h.getDailyTarget() : r.target(),
        r.unit() == null ? h.getUnit() : r.unit(),
        color(r.color(), h.getColor()),
        127);
    if (r.defaultHabit() != null) {
      h.setDefaultHabit(r.defaultHabit());
      if (r.defaultHabit()) habits.clearOtherDefaults(userId, h.getId());
    }
    return response(h);
  }

  public void delete(UUID userId, UUID habitId) {
    auth.lockUser(userId);
    Habit h = lock(userId, habitId);
    boolean wasDefault = h.isDefaultHabit();
    habits.delete(h);
    habits.flush();
    if (wasDefault && !habits.existsByUser_IdAndDefaultHabitTrue(userId)) {
      habits.findAllByUser_IdOrderByDefaultHabitDescNameAsc(userId).stream()
          .findFirst()
          .ifPresent(next -> next.setDefaultHabit(true));
    }
  }

  private Habit lock(UUID uid, UUID id) {
    return habits
        .lockOwned(id, uid)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Habit not found"));
  }

  private HabitResponse response(Habit h) {
    LocalDate day = LocalDate.now(clock.withZone(ZoneId.of(h.getUser().getTimezone())));
    int count =
        progress.findByHabit_IdAndDate(h.getId(), day).map(HabitProgress::getCount).orElse(0);
    return HabitResponse.from(h, count);
  }

  private String color(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.toUpperCase(Locale.ROOT);
  }

}
