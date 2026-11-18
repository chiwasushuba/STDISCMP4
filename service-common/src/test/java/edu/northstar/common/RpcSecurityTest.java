package edu.northstar.common;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import edu.northstar.contracts.*;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class RpcSecurityTest {
  private Server server;
  private ManagedChannel channel;
  private JwtEncoder encoder;

  @BeforeEach
  void start() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var key = generator.generateKeyPair();
    var jwk =
        new RSAKey.Builder((RSAPublicKey) key.getPublic())
            .privateKey((RSAPrivateKey) key.getPrivate())
            .build();
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    var service =
        new CourseApiGrpc.CourseApiImplBase() {
          @Override
          public void listCourses(CourseQuery request, StreamObserver<CourseList> observer) {
            Rpc.reply(
                observer,
                () -> {
                  Actor student = Actor.require("STUDENT");
                  return CourseList.newBuilder()
                      .addCourses(Course.newBuilder().setId(student.id()))
                      .build();
                });
          }
        };
    server =
        new RpcConfiguration()
            .rpcServer(0, List.of(service), Tokens.decoder((RSAPublicKey) key.getPublic()))
            .start();
    channel =
        ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
  }

  @AfterEach
  void stop() throws Exception {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private String token(String role, String issuer, String audience, Instant expiry) {
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),
                JwtClaimsSet.builder()
                    .issuer(issuer)
                    .audience(List.of(audience))
                    .subject("2024-001")
                    .claim("name", "Alex")
                    .claim("role", role)
                    .claim("sid", "session")
                    .issuedAt(expiry.minusSeconds(900))
                    .expiresAt(expiry)
                    .build()))
        .getTokenValue();
  }

  @Test
  void authenticatesAcrossTheActualGrpcTransport() {
    String token = token("STUDENT", Tokens.ISSUER, Tokens.AUDIENCE, Instant.now().plusSeconds(900));
    var result =
        CourseApiGrpc.newBlockingStub(Rpc.authenticated(channel, token))
            .withDeadlineAfter(3, TimeUnit.SECONDS)
            .listCourses(CourseQuery.getDefaultInstance());
    assertThat(result.getCourses(0).getId()).isEqualTo("2024-001");
  }

  @Test
  void rejectsMissingExpiredWrongIssuerAndWrongAudienceTokens() {
    assertThatThrownBy(
            () ->
                CourseApiGrpc.newBlockingStub(channel)
                    .listCourses(CourseQuery.getDefaultInstance()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            error ->
                assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    for (String token :
        List.of(
            token("STUDENT", "wrong", Tokens.AUDIENCE, Instant.now().plusSeconds(900)),
            token("STUDENT", Tokens.ISSUER, "wrong", Instant.now().plusSeconds(900)),
            token("STUDENT", Tokens.ISSUER, Tokens.AUDIENCE, Instant.now().minusSeconds(120)))) {
      assertThatThrownBy(
              () ->
                  CourseApiGrpc.newBlockingStub(Rpc.authenticated(channel, token))
                      .listCourses(CourseQuery.getDefaultInstance()))
          .isInstanceOfSatisfying(
              StatusRuntimeException.class,
              error ->
                  assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
    }
  }

  @Test
  void authenticatedFacultyStillCannotUseStudentOperations() {
    String token = token("FACULTY", Tokens.ISSUER, Tokens.AUDIENCE, Instant.now().plusSeconds(900));
    assertThatThrownBy(
            () ->
                CourseApiGrpc.newBlockingStub(Rpc.authenticated(channel, token))
                    .listCourses(CourseQuery.getDefaultInstance()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            error ->
                assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED));
  }
}
