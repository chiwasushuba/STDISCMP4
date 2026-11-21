package edu.animoroll.grade;

import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
abstract class DatabaseTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.12-alpine");

  private static final Path KEYS = keys();

  private static Path keys() {
    try {
      Path dir = Files.createTempDirectory("animoroll-test-");
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair key = generator.generateKeyPair();
      Files.writeString(
          dir.resolve("public.pem"),
          "-----BEGIN PUBLIC KEY-----\n"
              + Base64.getEncoder().encodeToString(key.getPublic().getEncoded())
              + "\n-----END PUBLIC KEY-----");
      Files.writeString(
          dir.resolve("private.pem"),
          "-----BEGIN PRIVATE KEY-----\n"
              + Base64.getEncoder().encodeToString(key.getPrivate().getEncoded())
              + "\n-----END PRIVATE KEY-----");
      dir.toFile().deleteOnExit();
      dir.resolve("public.pem").toFile().deleteOnExit();
      dir.resolve("private.pem").toFile().deleteOnExit();
      return dir;
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
    properties.add("rpc.server.port", () -> "0");
    properties.add("security.public-key", () -> KEYS.resolve("public.pem").toUri().toString());
    properties.add("security.private-key", () -> KEYS.resolve("private.pem").toUri().toString());
    properties.add("demo.enabled", () -> "true");
    properties.add("demo.password", () -> "Only-for-tests-2384!");
  }
}
