package com.habittracker.config;

import com.habittracker.repository.UserRepository;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
  @Bean
  public SecurityContextRepository securityContexts() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public UserDetailsService userDetailsService(UserRepository users) {
    return name ->
        users
            .findByUsername(name)
            .map(
                u ->
                    org.springframework.security.core.userdetails.User.withUsername(u.getUsername())
                        .password(u.getPasswordHash())
                        .roles("USER")
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
  }

  @Bean
  public SecurityFilterChain security(
      HttpSecurity http,
      SecurityContextRepository contexts,
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
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .securityContext(c -> c.securityContextRepository(contexts))
        .csrf(c -> c.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
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
            c -> c.authenticationEntryPoint((req, res, e) -> res.sendError(401, "Login required")));
    return http.build();
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
