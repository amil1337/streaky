package com.habittracker.config;

import com.habittracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityContextRepository securityContexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public CsrfTokenRepository csrfTokens() {
    return new HttpSessionCsrfTokenRepository();
  }

  @Bean
  public SecurityFilterChain security(
      HttpSecurity http,
      SecurityContextRepository contexts,
      CsrfTokenRepository csrfTokens,
      UserRepository users,
      @org.springframework.beans.factory.annotation.Qualifier("cors")
          CorsConfigurationSource corsSource)
      throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/health",
                        "/api/ready",
                        "/api/auth/csrf",
                        "/api/auth/register",
                        "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/api/**")
                    .access((authentication, request) -> {
                      Object principal = authentication.get().getPrincipal();
                      return new AuthorizationDecision(
                          principal instanceof UUID id && users.existsById(id));
                    })
                    .anyRequest()
                    .permitAll())
        .securityContext(c -> c.securityContextRepository(contexts))
        .csrf(c -> c.csrfTokenRepository(csrfTokens)
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .cors(c -> c.configurationSource(corsSource))
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c ->
                            c.policyDirectives(
                                "default-src 'self'; "
                                    + "base-uri 'self'; "
                                    + "connect-src 'self'; "
                                    + "font-src 'self'; "
                                    + "form-action 'self'; "
                                    + "frame-ancestors 'none'; "
                                    + "img-src 'self' data:; "
                                    + "object-src 'none'; "
                                    + "script-src 'self'; "
                                    + "style-src 'self' 'unsafe-inline'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
        .requestCache(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .logout(c -> c.disable())
        .exceptionHandling(
            c -> c
                .authenticationEntryPoint((req, res, e) -> problem(res, 401, "Login required"))
                .accessDeniedHandler((req, res, e) -> {
                  var authentication = SecurityContextHolder.getContext().getAuthentication();
                  if (!(e instanceof CsrfException)
                      && authentication != null && authentication.getPrincipal() instanceof UUID) {
                    problem(res, 401, "Login required");
                  } else {
                    problem(res, 403, "Request forbidden");
                  }
                }));
    return http.build();
  }

  private void problem(HttpServletResponse response, int status, String detail) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response.getWriter().write("""
        {"status":%d,"detail":"%s"}
        """.formatted(status, detail));
  }

  @Bean
  public CorsConfigurationSource cors(@Value("${app.allowed-origins:}") String allowed) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(allowed.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equals("*"))
            .toList());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN"));
    config.setAllowCredentials(true);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
