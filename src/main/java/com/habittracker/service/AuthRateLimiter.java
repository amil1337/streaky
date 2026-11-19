package com.habittracker.service;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthRateLimiter {
  private static final int MAX_TRACKED_WINDOWS = 10_000;

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;
  private final boolean enabled;

  public AuthRateLimiter(
      Clock clock, @Value("${app.auth-rate-limit.enabled:true}") boolean enabled) {
    this.clock = clock;
    this.enabled = enabled;
  }

  public void checkLogin(String clientAddress, String username) {
    if (!enabled) return;
    check("login:ip:" + clientAddress, 20, Duration.ofMinutes(15));
    check(
        "login:user:" + username.trim().toLowerCase(Locale.ROOT),
        10,
        Duration.ofMinutes(15));
  }

  public void checkRegistration(String clientAddress) {
    if (!enabled) return;
    check("register:ip:" + clientAddress, 5, Duration.ofHours(1));
  }

  private void check(String key, int limit, Duration duration) {
    long now = clock.millis();
    if (windows.size() >= MAX_TRACKED_WINDOWS && !windows.containsKey(key)) {
      windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      if (windows.size() >= MAX_TRACKED_WINDOWS)
        throw limited("Too many authentication attempts. Please try again later.");
    }

    Window current =
        windows.compute(
            key,
            (ignored, previous) ->
                previous == null || previous.expiresAt() <= now
                    ? new Window(now + duration.toMillis(), 1)
                    : new Window(previous.expiresAt(), previous.attempts() + 1));
    if (current.attempts() > limit)
      throw limited("Too many authentication attempts. Please try again later.");
  }

  private ResponseStatusException limited(String message) {
    return new ResponseStatusException(TOO_MANY_REQUESTS, message);
  }

  private record Window(long expiresAt, int attempts) {}
}
