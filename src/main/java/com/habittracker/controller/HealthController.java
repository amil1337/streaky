package com.habittracker.controller;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final JdbcTemplate database;

  public HealthController(JdbcTemplate database) {
    this.database = database;
  }

  @GetMapping("/api/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @GetMapping("/api/ready")
  public ResponseEntity<Map<String, String>> ready() {
    try {
      database.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok().cacheControl(CacheControl.noStore())
          .body(Map.of("status", "ok", "database", "reachable"));
    } catch (DataAccessException exception) {
      return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
          .body(Map.of("status", "unavailable", "database", "unreachable"));
    }
  }
}
