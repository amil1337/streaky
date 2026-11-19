package com.habittracker.controller;

import com.habittracker.dto.*;
import com.habittracker.service.AuthRateLimiter;
import com.habittracker.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final AuthRateLimiter rateLimiter;
  private final SecurityContextRepository contexts;

  public AuthController(
      AuthService auth, AuthRateLimiter rateLimiter, SecurityContextRepository contexts) {
    this.auth = auth;
    this.rateLimiter = rateLimiter;
    this.contexts = contexts;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(
      @Valid @RequestBody RegisterRequest r, HttpServletRequest request) {
    rateLimiter.checkRegistration(request.getRemoteAddr());
    return UserResponse.from(auth.register(r.username(), r.password(), r.timezone()));
  }

  @PostMapping("/login")
  public UserResponse login(
      @Valid @RequestBody LoginRequest r,
      HttpServletRequest request,
      HttpServletResponse response) {
    rateLimiter.checkLogin(request.getRemoteAddr(), r.username());
    var user = auth.login(r.username(), r.password());
    request.getSession();
    request.changeSessionId();
    request.getSession().setAttribute("userId", user.getId());
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            user.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    SecurityContextHolder.setContext(context);
    contexts.saveContext(context, request, response);
    return UserResponse.from(user);
  }

  @GetMapping("/me")
  public UserResponse me(HttpSession session) {
    return UserResponse.from(auth.getUser(SessionUser.id(session)));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(HttpSession session) {
    session.invalidate();
    SecurityContextHolder.clearContext();
  }
}
