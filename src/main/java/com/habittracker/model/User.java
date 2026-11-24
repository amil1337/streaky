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

  @Column(name = "username_label", length = 40)
  private String usernameLabel;

  @Column(nullable = false, length = 240)
  private String bio = "";

  @Column(nullable = false, length = 64)
  private String timezone = "UTC";

  protected User() {}

  public User(String username, String displayName, String passwordHash, String timezone) {
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.timezone = timezone;
  }

  public void update(String name, String bio, String timezone) {
    if (name != null) this.displayName = name.trim();
    this.bio = bio == null ? "" : bio.trim();
    this.timezone = timezone;
  }

  public void updateUsername(String username, String label) {
    this.username = username;
    this.usernameLabel = label;
  }

  public void updatePassword(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getPublicUsername() {
    if (usernameLabel != null) return usernameLabel;
    return username.equalsIgnoreCase(displayName) ? displayName : username;
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
