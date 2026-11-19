package com.habittracker.dto;

import java.util.List;

public record ActivityPage(
    List<ActivityResponse> items, int page, int size, long totalElements, boolean hasNext) {}
