package com.habittracker.controller;

import com.habittracker.dto.*;
import com.habittracker.service.AccountService;
import com.habittracker.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class ProfileController {
  private final AuthService auth;
  private final AccountService accounts;

  private final FindByIndexNameSessionRepository<? extends Session> sessions;

  public ProfileController(
      AuthService auth, AccountService accounts,
      FindByIndexNameSessionRepository<? extends Session> sessions) {
    this.auth = auth;
    this.accounts = accounts;
    this.sessions = sessions;
  }

  @PatchMapping
  public UserResponse update(@Valid @RequestBody ProfileRequest r, HttpSession s) {
    return UserResponse.from(auth.updateProfile(SessionUser.id(s), r));
  }

  @PutMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpSession session) {
    var userId = SessionUser.id(session);
    auth.changePassword(userId, request.currentPassword(), request.newPassword());
    revokeSessions(userId, session);
  }

  @GetMapping("/statistics")
  public ProfileStatisticsResponse statistics(HttpSession session) {
    return accounts.statistics(SessionUser.id(session));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@Valid @RequestBody DeleteAccountRequest request, HttpSession session) {
    var userId = SessionUser.id(session);
    accounts.delete(userId, request.currentPassword());
    revokeSessions(userId, session);
  }

  private void revokeSessions(java.util.UUID userId, HttpSession session) {
    session.invalidate();
    SecurityContextHolder.clearContext();
    sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
  }
}
