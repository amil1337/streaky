package com.habittracker.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
    name = "habit_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "progress_date"}))
public class HabitProgress {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "habit_id", nullable = false)
  private Habit habit;

  @Column(name = "progress_date", nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private int count;

  protected HabitProgress() {}

  public HabitProgress(Habit habit, LocalDate date) {
    this.habit = habit;
    this.date = date;
  }

  public void setCount(int count) {
    if (count < 0 || count > 9999) throw new IllegalArgumentException("Invalid count");
    this.count = count;
  }

  public UUID getId() {
    return id;
  }

  public Habit getHabit() {
    return habit;
  }

  public LocalDate getDate() {
    return date;
  }

  public int getCount() {
    return count;
  }
}
