package com.habittracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.habittracker.dto.*;
import com.habittracker.model.HabitType;
import com.habittracker.repository.UserRepository;
import com.habittracker.service.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:api_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "app.allowed-origins=https://portfolio.example",
      "app.auth-rate-limit.enabled=false"
    })
@AutoConfigureMockMvc
@Import(StreakyApiTests.TimeConfig.class)
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class StreakyApiTests {
  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-11-19T22:30:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired AuthService auth;
  @Autowired HabitService habits;
  @Autowired ProgressService progress;
  @Autowired ActivityService activity;
  @Autowired UserRepository users;

  record Client(Cookie session, String csrf) {}

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  String payload(Object data) {
    return json.writeValueAsString(data);
  }

  Client client(String zone) throws Exception {
    var first = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie session = first.getResponse().getCookie("SESSION");
    assertNotNull(session);
    String csrf = body(first).get("token").asText(),
        name = "u" + UUID.randomUUID().toString().replace("-", "");
    mvc.perform(
            post("/api/auth/register")
                .cookie(session)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("application/json")
                .content(
                    payload(
                        Map.of("username", name, "password", "portfolio123", "timezone", zone))))
        .andExpect(status().isCreated());
    String old = session.getValue();
    var loggedIn =
        mvc.perform(
            post("/api/auth/login")
                .cookie(session)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("application/json")
                .content(payload(Map.of("username", name, "password", "portfolio123"))))
            .andExpect(status().isOk())
            .andReturn();
    Cookie rotated = loggedIn.getResponse().getCookie("SESSION");
    assertNotNull(rotated);
    assertNotEquals(old, rotated.getValue());
    return new Client(rotated, csrf);
  }

  String create(Client c, String kind, int target) throws Exception {
    return body(mvc.perform(
                post("/api/habits")
                    .cookie(c.session)
                    .header("X-CSRF-TOKEN", c.csrf)
                    .contentType("application/json")
                    .content(
                        payload(
                            Map.of(
                                "name",
                                "Water",
                                "description",
                                "Daily water",
                                "kind",
                                kind,
                                "target",
                                target,
                                "unit",
                                "glasses"))))
            .andExpect(status().isCreated())
            .andReturn())
        .get("id")
        .asText();
  }

  MvcResult increment(Client c, String id) throws Exception {
    return mvc.perform(
            post("/api/habits/" + id + "/increment")
                .cookie(c.session)
                .header("X-CSRF-TOKEN", c.csrf))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  void sessionCsrfAndValidation() throws Exception {
    mvc.perform(get("/api/habits")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
        .andExpect(status().isForbidden());
    Client c = client("UTC");
    mvc.perform(get("/api/auth/me").cookie(c.session)).andExpect(status().isOk());
    mvc.perform(
            post("/api/habits")
                .cookie(c.session)
                .header("X-CSRF-TOKEN", c.csrf)
                .contentType("application/json")
                .content(payload(Map.of("name", " ", "target", 0))))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/auth/logout").cookie(c.session).header("X-CSRF-TOKEN", c.csrf))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void usernameLoginIsCaseInsensitiveAndDisplayNamePreservesCase() throws Exception {
    var first = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    Cookie session = first.getResponse().getCookie("SESSION");
    assertNotNull(session);
    String csrf = body(first).get("token").asText();
    String entered = "CaseUser" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    mvc.perform(
            post("/api/auth/register")
                .cookie(session)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("application/json")
                .content(
                    payload(
                        Map.of(
                            "username", entered,
                            "password", "portfolio123",
                            "timezone", "UTC"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value(entered.toLowerCase(Locale.ROOT)))
        .andExpect(jsonPath("$.name").value(entered));

    mvc.perform(
            post("/api/auth/login")
                .cookie(session)
                .header("X-CSRF-TOKEN", csrf)
                .contentType("application/json")
                .content(
                    payload(
                        Map.of(
                            "username", entered.toUpperCase(Locale.ROOT),
                            "password", "portfolio123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value(entered));
  }

  @Test
  void counterBeyondGoalAndNewestActivity() throws Exception {
    Client c = client("UTC");
    String id = create(c, "counter", 5);
    for (int i = 1; i <= 6; i++) assertEquals(i, body(increment(c, id)).get("count").asInt());
    mvc.perform(get("/api/habits/" + id + "/heatmap").cookie(c.session).param("days", "1"))
        .andExpect(jsonPath("$[0].percentage").value(100.0));
    mvc.perform(
            post("/api/habits/" + id + "/decrement")
                .cookie(c.session)
                .header("X-CSRF-TOKEN", c.csrf))
        .andExpect(jsonPath("$.count").value(5));
    var events =
        body(
            mvc.perform(get("/api/activity").cookie(c.session).param("size", "2"))
                .andExpect(status().isOk())
                .andReturn());
    assertEquals(7, events.get("totalElements").asInt());
    assertTrue(events.get("hasNext").asBoolean());
    assertEquals("decrement", events.get("items").get(0).get("action").asText());
    assertEquals(5, events.get("items").get(0).get("count").asInt());
    assertTrue(
        events.get("items").get(0).get("id").asLong()
            > events.get("items").get(1).get("id").asLong());
  }

  @Test
  void completionIsIdempotentAndOnlyToday() throws Exception {
    Client c = client("Asia/Tokyo");
    String id = create(c, "completion", 5);
    for (int i = 0; i < 2; i++)
      mvc.perform(
              put("/api/habits/" + id + "/completion")
                  .cookie(c.session)
                  .header("X-CSRF-TOKEN", c.csrf)
                  .contentType("application/json")
                  .content(payload(Map.of("completed", true))))
          .andExpect(jsonPath("$.count").value(1))
          .andExpect(jsonPath("$.date").value("2026-11-20"));
    mvc.perform(get("/api/activity").cookie(c.session))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            put("/api/habits/" + id + "/completion")
                .cookie(c.session)
                .header("X-CSRF-TOKEN", c.csrf)
                .contentType("application/json")
                .content(payload(Map.of("completed", false))))
        .andExpect(jsonPath("$.count").value(0));
    mvc.perform(
            post("/api/habits/" + id + "/decrement")
                .cookie(c.session)
                .header("X-CSRF-TOKEN", c.csrf))
        .andExpect(jsonPath("$.count").value(0));
    mvc.perform(get("/api/activity").cookie(c.session))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void ownershipDeleteAndProfile() throws Exception {
    Client a = client("UTC"), b = client("UTC");
    String id = create(a, "counter", 5);
    increment(a, id);
    mvc.perform(
            post("/api/habits/" + id + "/increment")
                .cookie(b.session)
                .header("X-CSRF-TOKEN", b.csrf))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/activity").cookie(b.session))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            patch("/api/users/me")
                .cookie(a.session)
                .header("X-CSRF-TOKEN", a.csrf)
                .contentType("application/json")
                .content(
                    payload(
                        Map.of("name", "Amil", "bio", "Portfolio", "timezone", "Europe/Istanbul"))))
        .andExpect(jsonPath("$.name").value("Amil"));
    mvc.perform(
            put("/api/habits/" + id)
                .cookie(a.session)
                .header("X-CSRF-TOKEN", a.csrf)
                .contentType("application/json")
                .content(payload(Map.of("name", "Water", "kind", "completion"))))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/habits/" + id).cookie(a.session).header("X-CSRF-TOKEN", a.csrf))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/habits").cookie(a.session)).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/activity").cookie(a.session))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void accountDeletionRemovesOwnedDataAndSession() throws Exception {
    Client c = client("UTC");
    String habitId = create(c, "counter", 3);
    increment(c, habitId);
    UUID userId =
        UUID.fromString(
            body(
                    mvc.perform(get("/api/auth/me").cookie(c.session))
                        .andExpect(status().isOk())
                        .andReturn())
                .get("id")
                .asText());

    mvc.perform(delete("/api/users/me").cookie(c.session).header("X-CSRF-TOKEN", c.csrf))
        .andExpect(status().isNoContent());

    assertFalse(users.existsById(userId));
    mvc.perform(get("/api/auth/me").cookie(c.session)).andExpect(status().isUnauthorized());
  }

  @Test
  void dateRangesAndActiveCounterStreak() throws Exception {
    Client c = client("UTC");
    String id = create(c, "counter", 5);
    increment(c, id);
    mvc.perform(get("/api/habits/" + id + "/statistics").cookie(c.session).param("days", "182"))
        .andExpect(jsonPath("$.currentStreak").value(1))
        .andExpect(jsonPath("$.completedDays").value(0));
    mvc.perform(get("/api/habits/" + id + "/heatmap").cookie(c.session).param("days", "364"))
        .andExpect(jsonPath("$.length()").value(364));
    mvc.perform(
            get("/api/habits/" + id + "/heatmap")
                .cookie(c.session)
                .param("start", "2024-02-28")
                .param("end", "2024-03-01"))
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[1].date").value("2024-02-29"));
    for (var params :
        List.of(
            Map.of("days", "367"),
            Map.of("start", "2026-11-19"),
            Map.of("start", "2026-11-20", "end", "2026-11-21"),
            Map.of("start", "2024-02-28", "end", "2024-03-01", "days", "7"))) {
      var req = get("/api/habits/" + id + "/heatmap").cookie(c.session);
      params.forEach((k, v) -> req.param(k, v));
      mvc.perform(req).andExpect(status().isBadRequest());
    }
  }

  @Test
  void corsUsesExplicitOrigin() throws Exception {
    mvc.perform(
            options("/api/habits")
                .header("Origin", "https://portfolio.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,X-CSRF-TOKEN"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://portfolio.example"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    mvc.perform(
            options("/api/habits")
                .header("Origin", "https://other.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
  }

  @Test
  void concurrentFirstIncrementsAreNotLost() throws Exception {
    var user =
        auth.register(
            "parallel_" + UUID.randomUUID().toString().substring(0, 8), "portfolio123", "UTC");
    var h =
        habits.create(
            user.getId(), new CreateHabitRequest("Counter", "", HabitType.COUNTER, 5, "times"));
    try (var pool = Executors.newFixedThreadPool(4)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 12; i++)
        futures.add(pool.submit(() -> progress.increment(user.getId(), h.id())));
      for (var f : futures) f.get(15, TimeUnit.SECONDS);
    }
    assertEquals(12, habits.getUserHabits(user.getId()).getFirst().todayCount());
    assertEquals(12, activity.getActivity(user.getId(), 0, 30).totalElements());
  }

  @Test
  void packagedFrontendAndHealthArePublic() throws Exception {
    mvc.perform(get("/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"auth-form\"")));
    mvc.perform(get("/app.js"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/auth/login")));
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
    mvc.perform(get("/api/ready"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.database").value("reachable"));
  }
}
