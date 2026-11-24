package com.habittracker.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "habits")
public class Habit {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(nullable = false, length = 240)
  private String description = "";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private HabitType kind = HabitType.COMPLETION;

  @Column(nullable = false)
  private int dailyTarget = 1;

  @Column(nullable = false, length = 20)
  private String unit = "times";

  @Column(nullable = false, length = 7)
  private String color = "#FF3434";

  @Column(name = "default_habit", nullable = false)
  private boolean defaultHabit;

  @Column(name = "tracking_days_mask", nullable = false)
  private int trackingDaysMask = 127;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<HabitProgress> progressEntries = new ArrayList<>();

  protected Habit() {}

  public Habit(
      String name,
      String description,
      HabitType kind,
      int target,
      String unit,
      String color,
      boolean defaultHabit,
      int trackingDaysMask,
      User user) {
    update(name, description, kind, target, unit, color, trackingDaysMask);
    this.defaultHabit = defaultHabit;
    this.user = user;
  }

  public void update(
      String name,
      String description,
      HabitType kind,
      int target,
      String unit,
      String color,
      int trackingDaysMask) {
    this.name = name.trim();
    this.description = description == null ? "" : description.trim();
    this.kind = kind;
    this.dailyTarget = kind == HabitType.COMPLETION ? 1 : target;
    this.unit = unit == null || unit.isBlank() ? "times" : unit.trim();
    this.color = color;
    this.trackingDaysMask = trackingDaysMask;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public HabitType getKind() {
    return kind;
  }

  public int getDailyTarget() {
    return dailyTarget;
  }

  public String getUnit() {
    return unit;
  }

  public String getColor() {
    return color;
  }

  public boolean isDefaultHabit() {
    return defaultHabit;
  }

  public void setDefaultHabit(boolean defaultHabit) {
    this.defaultHabit = defaultHabit;
  }

  public int getTrackingDaysMask() {
    return trackingDaysMask;
  }

  public List<Integer> getTrackingDays() {
    List<Integer> days = new ArrayList<>();
    for (int day = 1; day <= 7; day++) {
      if ((trackingDaysMask & (1 << (day - 1))) != 0) days.add(day);
    }
    return List.copyOf(days);
  }

  public int getWeeklyGoal() {
    return Integer.bitCount(trackingDaysMask);
  }

  public User getUser() {
    return user;
  }
}
