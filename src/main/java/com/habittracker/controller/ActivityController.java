package com.habittracker.controller;

import com.habittracker.dto.ActivityPage;
import com.habittracker.service.ActivityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {
  private final ActivityService events;

  public ActivityController(ActivityService events) {
    this.events = events;
  }

  @GetMapping
  public ActivityPage get(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "30") int size,
      HttpSession s) {
    return events.getActivity(SessionUser.id(s), page, size);
  }
}
