package com.habittracker.controller;

import com.habittracker.dto.*;
import com.habittracker.service.AccountService;
import com.habittracker.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class ProfileController {
  private final AuthService auth;
  private final AccountService accounts;

  public ProfileController(AuthService auth, AccountService accounts) {
    this.auth = auth;
    this.accounts = accounts;
  }

  @PatchMapping
  public UserResponse update(@Valid @RequestBody ProfileRequest r, HttpSession s) {
    return UserResponse.from(auth.updateProfile(SessionUser.id(s), r));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(HttpSession session) {
    accounts.delete(SessionUser.id(session));
    session.invalidate();
  }
}
