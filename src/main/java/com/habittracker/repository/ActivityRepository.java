package com.habittracker.repository;

import com.habittracker.model.ActivityEvent;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityEvent, Long> {
  Page<ActivityEvent> findByUserId(UUID userId, Pageable pageable);

  long deleteByUserId(UUID userId);
}
