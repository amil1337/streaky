package com.habittracker.dto;

public record StatisticsResponse(int currentStreak, int longestStreak, int completedDays) {}
