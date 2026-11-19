package com.habittracker.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class ClockConfig {
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
