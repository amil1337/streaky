package com.habittracker.controller;

import com.habittracker.dto.*;
import com.habittracker.service.ProgressService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/habits")
public class ProgressController {
  private final ProgressService progress;

  public ProgressController(ProgressService progress) {
    this.progress = progress;
  }

  @PostMapping("/{id}/increment")
  public ProgressResponse increment(@PathVariable UUID id, HttpSession s) {
    return ProgressResponse.from(progress.increment(SessionUser.id(s), id));
  }

  @PostMapping("/{id}/decrement")
  public ProgressResponse decrement(@PathVariable UUID id, HttpSession s) {
    return ProgressResponse.from(progress.decrement(SessionUser.id(s), id));
  }

  @PutMapping("/{id}/completion")
  public ProgressResponse completion(
      @PathVariable UUID id, @Valid @RequestBody CompletionRequest r, HttpSession s) {
    return ProgressResponse.from(progress.setCompletion(SessionUser.id(s), id, r.completed()));
  }

  @GetMapping("/{id}/heatmap")
  public List<HeatmapDayResponse> heatmap(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer days,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      HttpSession s) {
    return progress.getHeatmap(SessionUser.id(s), id, days, start, end);
  }

  @GetMapping("/{id}/statistics")
  public StatisticsResponse statistics(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer days,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      HttpSession s) {
    return progress.getStatistics(SessionUser.id(s), id, days, start, end);
  }
}
