package com.habittracker.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.habittracker.dto.*;
import com.habittracker.repository.ActivityRepository;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityService {
  private final ActivityRepository events;

  public ActivityService(ActivityRepository events) {
    this.events = events;
  }

  @Transactional(readOnly = true)
  public ActivityPage getActivity(UUID user, int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new ResponseStatusException(BAD_REQUEST, "Page must be >=0 and size 1–100");
    if ((long) page * size > Integer.MAX_VALUE)
      throw new ResponseStatusException(BAD_REQUEST, "Page offset is too large");
    var result =
        events.findByUserId(user, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
    return new ActivityPage(
        result.stream().map(ActivityResponse::from).toList(),
        page,
        size,
        result.getTotalElements(),
        result.hasNext());
  }
}
