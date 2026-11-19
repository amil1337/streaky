package com.habittracker.controller;

import com.habittracker.dto.*;
import com.habittracker.service.HabitService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/habits")
public class HabitController {
  private final HabitService habits;

  public HabitController(HabitService habits) {
    this.habits = habits;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public HabitResponse create(@Valid @RequestBody CreateHabitRequest r, HttpSession session) {
    return habits.create(SessionUser.id(session), r);
  }

  @GetMapping
  public List<HabitResponse> getAll(HttpSession session) {
    return habits.getUserHabits(SessionUser.id(session));
  }

  @PutMapping("/{id}")
  public HabitResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateHabitRequest r, HttpSession session) {
    return habits.update(SessionUser.id(session), id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id, HttpSession session) {
    habits.delete(SessionUser.id(session), id);
  }
}
