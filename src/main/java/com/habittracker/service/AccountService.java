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
  private final AuthService auth;
  private final HabitRepository habits;
  private final ActivityRepository events;

  public AccountService(
      UserRepository users, HabitRepository habits, ActivityRepository events, AuthService auth) {
    this.users = users;
    this.habits = habits;
    this.events = events;
    this.auth = auth;
  }

  @Transactional
  public void delete(UUID userId) {
    var user = auth.lockUser(userId);
    // Drain progress writers before deleting the account's activity history.
    var owned = habits.lockAllOwned(userId);
    events.deleteByUserId(userId);
    habits.deleteAll(owned);
    habits.flush();
    users.delete(user);
  }
}
