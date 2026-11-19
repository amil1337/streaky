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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<HabitProgress> progressEntries = new ArrayList<>();

  protected Habit() {}

  public Habit(
      String name, String description, HabitType kind, int target, String unit, User user) {
    update(name, description, kind, target, unit);
    this.user = user;
  }

  public void update(String name, String description, HabitType kind, int target, String unit) {
    this.name = name.trim();
    this.description = description == null ? "" : description.trim();
    this.kind = kind;
    this.dailyTarget = kind == HabitType.COMPLETION ? 1 : target;
    this.unit = unit == null || unit.isBlank() ? "times" : unit.trim();
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

  public User getUser() {
    return user;
  }
}
