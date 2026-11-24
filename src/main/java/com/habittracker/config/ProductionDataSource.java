package com.habittracker.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("production")
public class ProductionDataSource {
  private static final String DATABASE_NAME = "[A-Za-z0-9_][A-Za-z0-9_.-]*";

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  HikariDataSource dataSource(Environment environment) {
    var source = new HikariDataSource();
    source.setDriverClassName("org.postgresql.Driver");
    source.addDataSourceProperty("connectTimeout", 5);
    source.addDataSourceProperty("socketTimeout", 30);

    String managedUrl = environment.getProperty("DATABASE_URL");
    if (managedUrl == null || managedUrl.isBlank()) {
      configureFromFields(source, environment);
    } else {
      configureFromHeroku(source, managedUrl);
    }
    return source;
  }

  private void configureFromFields(HikariDataSource source, Environment environment) {
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
    if (!database.matches(DATABASE_NAME))
      throw new IllegalStateException("Invalid production configuration: DB_NAME");

    source.setJdbcUrl("jdbc:postgresql://%s:%d/%s".formatted(host, port, database));
    source.setUsername(user);
    source.setPassword(password);
  }

  private void configureFromHeroku(HikariDataSource source, String value) {
    try {
      URI url = URI.create(value);
      String scheme = url.getScheme();
      String path = url.getPath();
      String userInfo = url.getRawUserInfo();
      int port = url.getPort() == -1 ? 5432 : url.getPort();
      if (!("postgres".equals(scheme) || "postgresql".equals(scheme))
          || url.getHost() == null || port < 1 || port > 65535
          || path == null || !path.matches("/" + DATABASE_NAME)
          || userInfo == null || url.getFragment() != null) {
        throw new IllegalArgumentException();
      }
      String[] credentials = userInfo.split(":", 2);
      if (credentials.length != 2 || credentials[0].isBlank() || credentials[1].isBlank()) {
        throw new IllegalArgumentException();
      }
      source.setJdbcUrl("jdbc:postgresql://%s:%d%s".formatted(url.getHost(), port, path));
      source.setUsername(decodeUriCredential(credentials[0]));
      source.setPassword(decodeUriCredential(credentials[1]));
      source.addDataSourceProperty("sslmode", "require");
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid production configuration: DATABASE_URL");
    }
  }

  private String decodeUriCredential(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  private String required(Environment environment, String name) {
    String value = environment.getProperty(name);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing production configuration: " + name);
    return value;
  }
}
