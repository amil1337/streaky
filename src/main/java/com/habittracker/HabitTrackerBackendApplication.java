package com.habittracker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HabitTrackerBackendApplication {

  public static void main(String[] args) {
    var application = new SpringApplication(HabitTrackerBackendApplication.class);
    Path data = Path.of(".data");
    if (!Files.exists(data.resolve("davamly.mv.db")) && Files.isRegularFile(data.resolve("streaky.mv.db"))) {
      application.setDefaultProperties(Map.of("app.local-database-path", "./.data/streaky"));
    }
    application.run(args);
  }
}
