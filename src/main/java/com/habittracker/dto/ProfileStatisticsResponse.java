package com.habittracker.dto;

public record ProfileStatisticsResponse(
    int totalHabits, int completedDays, int currentStreak, int longestStreak) {}
