package com.habittracker.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HabitType {
  COMPLETION,
  COUNTER;

  @JsonCreator
  public static HabitType parse(String value) {
    return valueOf(value.toUpperCase(java.util.Locale.ROOT));
  }

  @JsonValue
  public String value() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
