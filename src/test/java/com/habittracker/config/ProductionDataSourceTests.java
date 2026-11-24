package com.habittracker.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionDataSourceTests {
  private final ProductionDataSource config = new ProductionDataSource();

  @Test
  void herokuCredentialsAreDecodedAndSentSeparatelyOverTls() {
    var environment = new MockEnvironment().withProperty("DATABASE_URL",
        "postgres://example_user:fixture%3A%40+value@db.example.test:15432/davamly");
    try (var source = config.dataSource(environment)) {
      assertEquals("jdbc:postgresql://db.example.test:15432/davamly", source.getJdbcUrl());
      assertEquals("example_user", source.getUsername());
      assertEquals("fixture:@+value", source.getPassword());
      assertEquals("require", source.getDataSourceProperties().getProperty("sslmode"));
    }
  }

  @Test
  void existingJdbcConfigurationUsesSeparateCredentialsAndKeepsDriverOptions() {
    var environment = new MockEnvironment()
        .withProperty("DB_URL", "jdbc:postgresql://db.example.test/davamly?sslmode=verify-full&channelBinding=require")
        .withProperty("DB_USER", "example_user")
        .withProperty("DB_PASSWORD", "fixture-value");
    try (var source = config.dataSource(environment)) {
      assertEquals("jdbc:postgresql://db.example.test:5432/davamly", source.getJdbcUrl());
      assertEquals("example_user", source.getUsername());
      assertEquals("fixture-value", source.getPassword());
      assertEquals("verify-full", source.getDataSourceProperties().getProperty("sslmode"));
      assertEquals("require", source.getDataSourceProperties().getProperty("channelBinding"));
    }
  }

  @Test
  void productionRejectsNonPostgresJdbcUrlsAndInlinePasswords() {
    for (String url : new String[] {
        "jdbc:h2:mem:example", "jdbc:postgresql://db.example.test/davamly?password=fixture-value"
    }) {
      var environment = new MockEnvironment().withProperty("DB_URL", url);
      var failure = assertThrows(IllegalStateException.class, () -> config.dataSource(environment));
      assertEquals("Invalid production configuration: DB_URL", failure.getMessage());
      assertNull(failure.getCause());
    }
  }

  @Test
  void invalidManagedUrlFailsWithoutEchoingItsContents() {
    var environment = new MockEnvironment().withProperty("DATABASE_URL",
        "postgres://example_user:fixture-value@db.example.test");
    var failure = assertThrows(IllegalStateException.class, () -> config.dataSource(environment));
    assertEquals("Invalid production configuration: DATABASE_URL", failure.getMessage());
    assertNull(failure.getCause());
  }
}
