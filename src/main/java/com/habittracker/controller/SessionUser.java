package com.habittracker.controller;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;

final class SessionUser {
  private SessionUser() {}

  static UUID id(HttpSession session) {
    Object value = session.getAttribute("userId");
    if (value instanceof UUID id) return id;
    throw new ResponseStatusException(UNAUTHORIZED, "Login required");
  }
}
