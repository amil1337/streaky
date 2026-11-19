package com.habittracker;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.habittracker.service.AuthRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AuthRateLimiterTests {
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void limitsRepeatedLoginAttemptsByUsername() {
    var limiter = new AuthRateLimiter(clock, true);
    for (int attempt = 0; attempt < 10; attempt++)
      limiter.checkLogin("192.0.2." + attempt, "demo-user");

    assertThrows(
        ResponseStatusException.class,
        () -> limiter.checkLogin("192.0.2.100", "demo-user"));
  }

  @Test
  void limitsRepeatedRegistrationsByAddress() {
    var limiter = new AuthRateLimiter(clock, true);
    for (int attempt = 0; attempt < 5; attempt++)
      limiter.checkRegistration("192.0.2.1");

    assertThrows(
        ResponseStatusException.class, () -> limiter.checkRegistration("192.0.2.1"));
  }
}
