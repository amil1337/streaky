package com.habittracker.dto;

import java.time.LocalDate;

public record HeatmapDayResponse(LocalDate date, int count, double percentage) {}
