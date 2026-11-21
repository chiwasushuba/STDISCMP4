package edu.northstar.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalViewController {
  @GetMapping({"/", "/login", "/courses", "/enrollment", "/grades", "/faculty"})
  String portal() {
    return "forward:/index.html";
  }
}
