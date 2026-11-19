package com.habittracker.model;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(
    name = "activity_events",
    indexes = @Index(name = "activity_user_sequence", columnList = "user_id,id"))
public class ActivityEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  // Snapshots intentionally survive deleting a habit; no cascading habit FK.
  @Column(nullable = false)
  private UUID habitId;

  @Column(nullable = false, length = 60)
  private String name;

  @Column(nullable = false, length = 16)
  private String action;

  @Column(nullable = false)
  private int count;

  @Column(nullable = false, length = 20)
  private String unit;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private Instant createdAt;

  protected ActivityEvent() {}

  public ActivityEvent(Habit h, String action, int count, LocalDate date, Instant now) {
    this.userId = h.getUser().getId();
    this.habitId = h.getId();
    this.name = h.getName();
    this.action = action;
    this.count = count;
    this.unit = h.getUnit();
    this.date = date;
    this.createdAt = now;
  }

  public Long getId() {
    return id;
  }

  public UUID getHabitId() {
    return habitId;
  }

  public String getName() {
    return name;
  }

  public String getAction() {
    return action;
  }

  public int getCount() {
    return count;
  }

  public String getUnit() {
    return unit;
  }

  public LocalDate getDate() {
    return date;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
