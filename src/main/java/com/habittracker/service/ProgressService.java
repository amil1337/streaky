package com.habittracker.service;

import static org.springframework.http.HttpStatus.*;

import com.habittracker.dto.*;
import com.habittracker.model.*;
import com.habittracker.repository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProgressService {
  private final HabitRepository habits;
  private final HabitProgressRepository progress;
  private final ActivityRepository events;
  private final Clock clock;

  public ProgressService(
      HabitRepository habits,
      HabitProgressRepository progress,
      ActivityRepository events,
      Clock clock) {
    this.habits = habits;
    this.progress = progress;
    this.events = events;
    this.clock = clock;
  }

  public HabitProgress increment(UUID uid, UUID id) {
    return change(uid, id, 1, null);
  }

  public HabitProgress decrement(UUID uid, UUID id) {
    return change(uid, id, -1, null);
  }

  public HabitProgress setCompletion(UUID uid, UUID id, boolean completed) {
    return change(uid, id, 0, completed);
  }

  private HabitProgress change(UUID uid, UUID id, int delta, Boolean completion) {
    // Lock the parent row: serializes updates even before the first progress row exists.
    Habit h = owned(uid, id, true);
    LocalDate day = today(h);
    HabitProgress p =
        progress.findByHabit_IdAndDate(id, day).orElseGet(() -> new HabitProgress(h, day));
    int old = p.getCount(), next;
    if (completion != null) {
      if (h.getKind() != HabitType.COMPLETION)
        throw new ResponseStatusException(BAD_REQUEST, "Use increment/decrement for a counter");
      next = completion ? 1 : 0;
    } else
      next = Math.max(0, Math.min(h.getKind() == HabitType.COMPLETION ? 1 : 9999, old + delta));
    if (old == next) return p;
    p.setCount(next);
    progress.save(p);
    String action =
        h.getKind() == HabitType.COMPLETION
            ? (next == 1 ? "complete" : "undo")
            : (delta > 0 ? "increment" : "decrement");
    events.save(new ActivityEvent(h, action, next, day, clock.instant()));
    return p;
  }

  @Transactional(readOnly = true)
  public List<HeatmapDayResponse> getHeatmap(
      UUID uid, UUID id, Integer days, LocalDate start, LocalDate end) {
    Habit h = owned(uid, id, false);
    LocalDate[] range = range(h, days, start, end);
    Map<LocalDate, Integer> counts = counts(h.getId(), range);
    List<HeatmapDayResponse> result = new ArrayList<>();
    for (LocalDate d = range[0]; !d.isAfter(range[1]); d = d.plusDays(1)) {
      int n = counts.getOrDefault(d, 0);
      result.add(new HeatmapDayResponse(d, n, Math.min(100.0, n * 100.0 / h.getDailyTarget())));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public StatisticsResponse getStatistics(
      UUID uid, UUID id, Integer days, LocalDate start, LocalDate end) {
    Habit h = owned(uid, id, false);
    LocalDate[] range = range(h, days, start, end);
    Map<LocalDate, Integer> counts = counts(id, range);
    int best = 0, run = 0, completed = 0;
    for (LocalDate d = range[0]; !d.isAfter(range[1]); d = d.plusDays(1)) {
      int n = counts.getOrDefault(d, 0);
      if (n >= h.getDailyTarget()) completed++;
      run = n > 0 ? run + 1 : 0;
      best = Math.max(best, run);
    }
    LocalDate d = range[1];
    if (d.equals(today(h)) && counts.getOrDefault(d, 0) == 0) d = d.minusDays(1);
    int current = 0;
    while (!d.isBefore(range[0]) && counts.getOrDefault(d, 0) > 0) {
      current++;
      d = d.minusDays(1);
    }
    return new StatisticsResponse(current, best, completed);
  }

  private Map<LocalDate, Integer> counts(UUID id, LocalDate[] range) {
    Map<LocalDate, Integer> result = new HashMap<>();
    progress
        .findAllByHabit_IdAndDateBetweenOrderByDateAsc(id, range[0], range[1])
        .forEach(p -> result.put(p.getDate(), p.getCount()));
    return result;
  }

  private LocalDate[] range(Habit h, Integer days, LocalDate start, LocalDate end) {
    LocalDate now = today(h);
    if (start != null || end != null) {
      if (start == null || end == null || days != null)
        throw new ResponseStatusException(BAD_REQUEST, "Provide start/end together, or days");
    } else {
      int size = days == null ? 182 : days;
      if (size < 1 || size > 366)
        throw new ResponseStatusException(BAD_REQUEST, "Days must be 1–366");
      end = now;
      start = end.minusDays(size - 1L);
    }
    if (start.isAfter(end) || end.isAfter(now) || ChronoUnit.DAYS.between(start, end) >= 366)
      throw new ResponseStatusException(
          BAD_REQUEST, "Invalid range: at most 366 days, not in the future");
    return new LocalDate[] {start, end};
  }

  private Habit owned(UUID uid, UUID id, boolean lock) {
    return (lock ? habits.lockOwned(id, uid) : habits.findByIdAndUser_Id(id, uid))
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Habit not found"));
  }

  private LocalDate today(Habit h) {
    return LocalDate.now(clock.withZone(ZoneId.of(h.getUser().getTimezone())));
  }
}
