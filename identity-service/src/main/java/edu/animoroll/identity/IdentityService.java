package edu.animoroll.identity;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import edu.animoroll.common.*;
import edu.animoroll.contracts.*;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
  private final JdbcTemplate jdbc;
  private final JwtEncoder encoder;
  private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
  private final String dummyHash = passwords.encode(UUID.randomUUID().toString());

  public IdentityService(
      JdbcTemplate jdbc,
      @Value("${security.private-key}") Resource privateKey,
      @Value("${security.public-key}") Resource publicKey)
      throws Exception {
    this.jdbc = jdbc;
    RSAKey rsa =
        new RSAKey.Builder(Tokens.publicKey(publicKey))
            .privateKey(Tokens.privateKey(privateKey))
            .keyID("animoroll-1")
            .build();
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsa)));
  }

  @Transactional
  public AuthReply login(LoginRequest request) {
    Rpc.check(
        request.getEmail().length() <= 200 && request.getPassword().length() <= 100,
        "Check your email and password.");
    if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
      throw Status.UNAUTHENTICATED
          .withDescription("Email or password is incorrect.")
          .asRuntimeException();
    }
    List<Map<String, Object>> accounts =
        jdbc.queryForList(
            "select * from account where email = ?",
            request.getEmail().trim().toLowerCase(Locale.ROOT));
    String hash =
        accounts.isEmpty() ? dummyHash : (String) accounts.getFirst().get("password_hash");
    if (!passwords.matches(request.getPassword(), hash) || accounts.isEmpty()) {
      throw Status.UNAUTHENTICATED
          .withDescription("Email or password is incorrect.")
          .asRuntimeException();
    }
    Map<String, Object> account = accounts.getFirst();
    UUID session = UUID.randomUUID();
    String refresh = refreshToken();
    jdbc.update(
        "insert into login_session(id, account_id, refresh_hash, expires_at) values (?, ?, ?, ?)",
        session,
        account.get("id"),
        digest(refresh),
        Timestamp.from(Instant.now().plusSeconds(28800)));
    return issue(account, session.toString(), refresh);
  }

  @Transactional
  public AuthReply refresh(String token) {
    Rpc.check(token.length() >= 40 && token.length() <= 100, "Invalid refresh session.");
    var rows =
        jdbc.queryForList(
            "select s.id as session_id, a.* from login_session s join account a on a.id=s.account_id where s.refresh_hash=? and not s.revoked and s.expires_at>now() for update of s",
            digest(token));
    if (rows.isEmpty())
      throw Status.UNAUTHENTICATED.withDescription("Please sign in again.").asRuntimeException();
    var row = rows.getFirst();
    String refresh = refreshToken();
    jdbc.update(
        "update login_session set refresh_hash=? where id=?",
        digest(refresh),
        row.get("session_id"));
    return issue(row, row.get("session_id").toString(), refresh);
  }

  public Receipt logout() {
    Actor actor = Actor.current();
    jdbc.update(
        "update login_session set revoked=true where id=? and account_id=?",
        UUID.fromString(actor.sessionId()),
        actor.id());
    return Receipt.newBuilder().setMessage("Signed out.").build();
  }

  private AuthReply issue(Map<String, Object> account, String session, String refresh) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(Tokens.ISSUER)
            .audience(List.of(Tokens.AUDIENCE))
            .subject((String) account.get("id"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .claim("name", account.get("display_name"))
            .claim("role", account.get("role"))
            .claim("sid", session)
            .build();
    String access =
        encoder
            .encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
            .getTokenValue();
    return AuthReply.newBuilder()
        .setAccessToken(access)
        .setRefreshToken(refresh)
        .setName((String) account.get("display_name"))
        .setRole((String) account.get("role"))
        .setUserId((String) account.get("id"))
        .build();
  }

  private static String refreshToken() {
    byte[] bytes = new byte[48];
    new SecureRandom().nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String digest(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
