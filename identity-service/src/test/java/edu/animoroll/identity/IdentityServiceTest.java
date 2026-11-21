package edu.animoroll.identity;

import static org.assertj.core.api.Assertions.*;

import edu.animoroll.common.*;
import edu.animoroll.contracts.*;
import io.grpc.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;

@SpringBootTest
class IdentityServiceTest extends DatabaseTest {
  @Autowired IdentityService service;
  @Autowired JwtDecoder decoder;

  private AuthReply login() {
    return service.login(
        LoginRequest.newBuilder()
            .setEmail("student@animoroll.edu")
            .setPassword("Only-for-tests-2384!")
            .build());
  }

  @Test
  void signedTokenContainsCrossNodeIdentityAndSession() {
    var reply = login();
    Actor actor = Tokens.actor(decoder, reply.getAccessToken());
    assertThat(actor.id()).isEqualTo("2024-001");
    assertThat(actor.role()).isEqualTo("STUDENT");
    assertThat(actor.sessionId()).isNotBlank();
    String token = reply.getAccessToken();
    String[] pieces = token.split("\\.");
    pieces[1] =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"sub\":\"FAC-001\"}".getBytes());
    assertThatThrownBy(() -> decoder.decode(String.join(".", pieces)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void refreshRotatesAndLogoutRevokesThePersistentSession() throws Exception {
    var first = login();
    var refreshed = service.refresh(first.getRefreshToken());
    assertThat(refreshed.getRefreshToken()).isNotEqualTo(first.getRefreshToken());
    assertThatThrownBy(() -> service.refresh(first.getRefreshToken()))
        .isInstanceOf(StatusRuntimeException.class);
    Actor actor = Tokens.actor(decoder, refreshed.getAccessToken());
    Context.current().withValue(Actor.CURRENT, actor).call(service::logout);
    assertThatThrownBy(() -> service.refresh(refreshed.getRefreshToken()))
        .isInstanceOf(StatusRuntimeException.class);
    // Offline signature validation deliberately permits the already issued access token until
    // expiry.
    assertThat(decoder.decode(refreshed.getAccessToken()).getSubject()).isEqualTo("2024-001");
  }

  @Test
  void wrongCredentialsAreRejected() {
    assertThatThrownBy(
            () ->
                service.login(
                    LoginRequest.newBuilder()
                        .setEmail("student@animoroll.edu")
                        .setPassword("incorrect")
                        .build()))
        .isInstanceOf(StatusRuntimeException.class);
  }

  @Test
  void multibytePasswordBeyondBcryptLimitIsAnAuthenticationFailure() {
    assertThatThrownBy(
            () ->
                service.login(
                    LoginRequest.newBuilder()
                        .setEmail("student@animoroll.edu")
                        .setPassword("é".repeat(40))
                        .build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            error ->
                assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
  }
}
