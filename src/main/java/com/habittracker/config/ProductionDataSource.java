package com.habittracker.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("production")
public class ProductionDataSource {

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource dataSource(Environment environment) {
    String host = required(environment, "DB_HOST");
    String database = required(environment, "DB_NAME");
    String user = required(environment, "DB_USER");
    String password = required(environment, "DB_PASSWORD");
    int port;
    try {
      port = Integer.parseInt(environment.getProperty("DB_PORT", "5432"));
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid production configuration: DB_PORT");
    }
    if (port < 1 || port > 65535)
      throw new IllegalStateException("Invalid production configuration: DB_PORT");
    if (!host.matches("[A-Za-z0-9][A-Za-z0-9.-]*|\\[[0-9A-Fa-f:]+\\]"))
      throw new IllegalStateException("Invalid production configuration: DB_HOST");
    if (!database.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*"))
      throw new IllegalStateException("Invalid production configuration: DB_NAME");

    var source = new HikariDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.setJdbcUrl("jdbc:postgresql://%s:%d/%s".formatted(host, port, database));
    source.setUsername(user);
    source.setPassword(password);
    source.addDataSourceProperty("connectTimeout", 5);
    source.addDataSourceProperty("socketTimeout", 30);
    return source;
  }

  private String required(Environment environment, String name) {
    String value = environment.getProperty(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing production configuration: " + name);
    return value;
  }
}
