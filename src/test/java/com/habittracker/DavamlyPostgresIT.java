package com.habittracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.habittracker.dto.CreateHabitRequest;
import com.habittracker.model.HabitType;
import com.habittracker.service.AccountService;
import java.util.UUID;
import java.util.concurrent.*;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

@ActiveProfiles("production")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_IT", matches = "true")
class DavamlyPostgresIT extends DavamlyApiContract {
  @Autowired AccountService accounts;
  @Autowired TransactionTemplate transactions;

  @Test
  void duplicateRegistrationsCannotCreateTwoAccounts() throws Exception {
    String name = "duplicate_" + UUID.randomUUID().toString().substring(0, 8);
    var start = new CountDownLatch(1);
    Callable<Boolean> register = () -> {
      assertTrue(start.await(5, TimeUnit.SECONDS));
      try {
        auth.register(name, "fixture-only-password", "UTC");
        return true;
      } catch (ResponseStatusException e) {
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        return false;
      } catch (DataIntegrityViolationException e) {
        return false;
      }
    };
    try (var threads = Executors.newFixedThreadPool(2)) {
      var first = threads.submit(register);
      var second = threads.submit(register);
      start.countDown();
      assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    }
    assertEquals(1, jdbc.queryForObject(
        "select count(*) from app_users where username = ?", Integer.class, name));
  }

  @Test
  void accountDeletionWaitsForInFlightProgressAndRemovesItsEvent() throws Exception {
    var user = auth.register("race_" + UUID.randomUUID().toString().substring(0, 8),
        "fixture-only-password", "UTC");
    var habit = habits.create(user.getId(),
        new CreateHabitRequest("Counter", "", HabitType.COUNTER, 5, "times"));
    var written = new CountDownLatch(1);
    var release = new CountDownLatch(1);

    try (var threads = Executors.newFixedThreadPool(2)) {
      var writer = threads.submit(() -> transactions.executeWithoutResult(status -> {
        progress.increment(user.getId(), habit.id());
        written.countDown();
        try {
          assertTrue(release.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
      }));

      try {
        assertTrue(written.await(5, TimeUnit.SECONDS));
        var deletion = threads.submit(() -> accounts.delete(user.getId()));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean blocked = false;
        while (!blocked && System.nanoTime() < deadline) {
          blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
              select exists(select 1 from pg_stat_activity
                where datname = current_database() and wait_event_type = 'Lock'
                  and cardinality(pg_blocking_pids(pid)) > 0)
              """, Boolean.class));
          if (!blocked) Thread.sleep(25);
        }
        assertTrue(blocked, "Account deletion did not wait for the active progress transaction");
        release.countDown();
        writer.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);
      } finally {
        release.countDown();
      }
    }

    assertFalse(users.existsById(user.getId()));
    assertEquals(0, jdbc.queryForObject(
        "select count(*) from activity_events where user_id = ?", Integer.class, user.getId()));
    assertEquals(0, jdbc.queryForObject(
        "select count(*) from habit_progress where habit_id = ?", Integer.class, habit.id()));
  }

  @Test
  void validatesProductionSchemaOnPostgresAndProtectsCookies() throws Exception {
    assertEquals(2, jdbc.queryForObject(
        "select count(*) from flyway_schema_history where version in ('1', '2') and success",
        Integer.class));
    var version = jdbc.queryForObject("select version()", String.class);
    assertTrue(version.startsWith("PostgreSQL"));
    mvc.perform(get("/api/auth/csrf").secure(true))
        .andExpect(status().isOk())
        .andExpect(header().string("Strict-Transport-Security", Matchers.containsString("max-age")))
        .andExpect(header().string("Content-Security-Policy", Matchers.containsString("frame-ancestors 'none'")))
        .andExpect(header().string("Set-Cookie", Matchers.allOf(
            Matchers.containsString("Secure"),
            Matchers.containsString("HttpOnly"),
            Matchers.containsString("SameSite=Lax"))));
  }
}
