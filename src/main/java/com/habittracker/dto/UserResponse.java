package com.habittracker.dto;

import com.habittracker.model.User;
import java.util.UUID;

public record UserResponse(
    UUID id, String username, String name, String bio, String timezone, String usernameLabel) {
  public static UserResponse from(User u) {
    return new UserResponse(
        u.getId(), u.getUsername(), u.getDisplayName(), u.getBio(), u.getTimezone(),
        u.getPublicUsername());
  }
}
