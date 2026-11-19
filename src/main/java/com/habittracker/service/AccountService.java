package com.habittracker.service;

import com.habittracker.repository.ActivityRepository;
import com.habittracker.repository.HabitRepository;
import com.habittracker.repository.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final UserRepository users;
  private final HabitRepository habits;
  private final ActivityRepository events;

  public AccountService(
      UserRepository users, HabitRepository habits, ActivityRepository events) {
    this.users = users;
    this.habits = habits;
    this.events = events;
  }

  @Transactional
  public void delete(UUID userId) {
    events.deleteByUserId(userId);
    habits.deleteAll(habits.findAllByUser_IdOrderByNameAsc(userId));
    habits.flush();
    users.deleteById(userId);
  }
}
