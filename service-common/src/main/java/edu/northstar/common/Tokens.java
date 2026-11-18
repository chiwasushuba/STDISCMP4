package edu.northstar.common;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

public final class Tokens {
  public static final String ISSUER = "northstar-identity";
  public static final String AUDIENCE = "northstar-enrollment";

  private Tokens() {}

  private static byte[] der(Resource resource) throws Exception {
    String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.US_ASCII);
    return Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
  }

  public static RSAPublicKey publicKey(Resource resource) throws Exception {
    return (RSAPublicKey)
        KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(resource)));
  }

  public static RSAPrivateKey privateKey(Resource resource) throws Exception {
    return (RSAPrivateKey)
        KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der(resource)));
  }

  public static JwtDecoder decoder(RSAPublicKey key) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(ISSUER), audience));
    return decoder;
  }

  public static Actor actor(JwtDecoder decoder, String token) {
    Jwt jwt = decoder.decode(token);
    return new Actor(
        jwt.getSubject(),
        jwt.getClaimAsString("name"),
        jwt.getClaimAsString("role"),
        jwt.getClaimAsString("sid"),
        token);
  }
}
