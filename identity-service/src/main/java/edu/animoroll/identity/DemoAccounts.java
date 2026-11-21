package edu.animoroll.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "demo.enabled", havingValue = "true")
public class DemoAccounts implements CommandLineRunner {
  private final JdbcTemplate jdbc;
  private final String password;

  public DemoAccounts(JdbcTemplate jdbc, @Value("${demo.password}") String password) {
    this.jdbc = jdbc;
    this.password = password;
  }

  @Override
  public void run(String... args) {
    if (password.length() < 12)
      throw new IllegalStateException("DEMO_PASSWORD must contain at least 12 characters.");
    var encoder = new BCryptPasswordEncoder();
    add("2024-001", "student@animoroll.edu", "Alex Morgan", "STUDENT", encoder.encode(password));
    add("2024-002", "student2@animoroll.edu", "Sam Rivera", "STUDENT", encoder.encode(password));
    add(
        "FAC-001",
        "faculty@animoroll.edu",
        "Dr. Jordan Reyes",
        "FACULTY",
        encoder.encode(password));
  }

  private void add(String id, String email, String name, String role, String hash) {
    jdbc.update(
        "insert into account values (?, ?, ?, ?, ?) on conflict (id) do nothing",
        id,
        email,
        name,
        role,
        hash);
  }
}
