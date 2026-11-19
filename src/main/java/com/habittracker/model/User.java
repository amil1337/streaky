package com.habittracker.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 40)
  private String username;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false, length = 40)
  private String displayName;

  @Column(nullable = false, length = 240)
  private String bio = "";

  @Column(nullable = false, length = 64)
  private String timezone = "UTC";

  protected User() {}

  public User(String username, String passwordHash, String timezone) {
    this.username = username;
    this.displayName = username;
    this.passwordHash = passwordHash;
    this.timezone = timezone;
  }

  public void update(String name, String bio, String timezone) {
    this.displayName = name.trim();
    this.bio = bio == null ? "" : bio.trim();
    this.timezone = timezone;
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getBio() {
    return bio;
  }

  public String getTimezone() {
    return timezone;
  }
}
