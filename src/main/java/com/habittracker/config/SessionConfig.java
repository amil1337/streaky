package com.habittracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
public class SessionConfig {

  @Bean
  DefaultCookieSerializer cookieSerializer(
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
    var serializer = new DefaultCookieSerializer();
    serializer.setUseSecureCookie(secure);
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    return serializer;
  }
}
