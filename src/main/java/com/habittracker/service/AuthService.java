package com.habittracker.service;

import static org.springframework.http.HttpStatus.*;

import com.habittracker.dto.ProfileRequest;
import com.habittracker.model.User;
import com.habittracker.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthService {
  private final UserRepository users;
  private final BCryptPasswordEncoder passwords;
  private final String unknownUserHash;

  public AuthService(UserRepository users, BCryptPasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
    this.unknownUserHash = passwords.encode(UUID.randomUUID().toString());
  }

  public User register(String username, String password, String timezone) {
    String displayName = username.trim();
    String clean = normalizeUsername(username);
    checkPassword(password);
    if (users.existsByUsername(clean))
      throw new ResponseStatusException(CONFLICT, "Username already exists");
    return users.saveAndFlush(
        new User(clean, displayName, passwords.encode(password), zone(timezone, "UTC")));
  }

  @Transactional(readOnly = true)
  public User login(String username, String password) {
    checkPassword(password);
    User user = users.findByUsername(normalizeUsername(username)).orElse(null);
    String hash = user == null ? unknownUserHash : user.getPasswordHash();
    if (!passwords.matches(password, hash) || user == null)
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid username or password");
    return user;
  }

  @Transactional(readOnly = true)
  public User getUser(UUID id) {
    return users
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Login required"));
  }

  public User lockUser(UUID id) {
    return users.lockById(id)
        .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Login required"));
  }

  public User updateProfile(UUID id, ProfileRequest request) {
    User user = lockUser(id);
    if (request.username() != null) {
      String key = normalizeUsername(request.username());
      if (!key.equals(user.getUsername()) && users.existsByUsername(key))
        throw new ResponseStatusException(CONFLICT, "Username already exists");
      user.updateUsername(key, request.username());
    }
    if (request.name() != null && request.name().isBlank())
      throw new ResponseStatusException(BAD_REQUEST, "Name cannot be blank");
    user.update(
        request.name(), request.bio() == null ? user.getBio() : request.bio(),
        zone(request.timezone(), user.getTimezone()));
    return user;
  }

  public void changePassword(UUID id, String currentPassword, String newPassword) {
    User user = lockUser(id);
    verifyPassword(user, currentPassword);
    checkPassword(newPassword);
    user.updatePassword(passwords.encode(newPassword));
  }

  void verifyPassword(User user, String password) {
    checkPassword(password);
    if (!passwords.matches(password, user.getPasswordHash()))
      throw new ResponseStatusException(UNAUTHORIZED, "Invalid password");
  }

  private String zone(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return ZoneId.of(value).getId();
    } catch (java.time.DateTimeException e) {
      throw new ResponseStatusException(BAD_REQUEST, "Invalid timezone");
    }
  }

  private void checkPassword(String value) {
    if (value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
  }

  private String normalizeUsername(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
