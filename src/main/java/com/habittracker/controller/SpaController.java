package com.habittracker.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({"/", "/login", "/signup", "/app", "/activity", "/profile"})
  public String application() {
    return "forward:/index.html";
  }
}
