package com.habittracker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:rate_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "app.auth-rate-limit.enabled=true"
})
@AutoConfigureMockMvc
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class DavamlyRateLimitApiTests {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void loginAndRegistrationReturn429AtTheirLimits() throws Exception {
    var csrf = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
    var cookie = csrf.getResponse().getCookie("SESSION");
    assertNotNull(cookie);
    var token = json.readTree(csrf.getResponse().getContentAsString()).get("token").asText();

    for (int attempt = 0; attempt < 11; attempt++) {
      mvc.perform(post("/api/auth/login").cookie(cookie).header("X-CSRF-TOKEN", token)
              .contentType("application/json")
              .content("""
                  {"username":"unknown_limit_test","password":"fixture-only-password"}
                  """))
          .andExpect(status().is(attempt < 10 ? 401 : 429));
    }
    for (int attempt = 0; attempt < 6; attempt++) {
      mvc.perform(post("/api/auth/register").cookie(cookie).header("X-CSRF-TOKEN", token)
              .contentType("application/json")
              .content("""
                  {"username":"signup_limit_test","password":"fixture-only-password"}
                  """))
          .andExpect(status().is(attempt == 0 ? 201 : attempt < 5 ? 409 : 429));
    }
  }
}
