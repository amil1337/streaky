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
  void invalidManagedUrlFailsWithoutEchoingItsContents() {
    var environment = new MockEnvironment().withProperty("DATABASE_URL",
        "postgres://example_user:fixture-value@db.example.test");
    var failure = assertThrows(IllegalStateException.class, () -> config.dataSource(environment));
    assertEquals("Invalid production configuration: DATABASE_URL", failure.getMessage());
    assertNull(failure.getCause());
  }
}
