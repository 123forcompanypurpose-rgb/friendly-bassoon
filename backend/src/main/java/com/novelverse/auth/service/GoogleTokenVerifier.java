package com.novelverse.auth.service;

import com.novelverse.common.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class GoogleTokenVerifier {

  private final String clientId;
  private final JwtDecoder decoder;

  public GoogleTokenVerifier(@Value("${app.google.client-id}") String clientId) {
    this.clientId = clientId;
    NimbusJwtDecoder googleDecoder =
        NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build();
    OAuth2TokenValidator<Jwt> issuerValidator =
        JwtValidators.createDefaultWithIssuer("https://accounts.google.com");
    OAuth2TokenValidator<Jwt> audienceValidator =
        jwt -> {
          List<String> audiences = jwt.getAudience();
          if (clientId.isBlank() || audiences == null || !audiences.contains(clientId)) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Google token audience is not accepted.", null));
          }
          return OAuth2TokenValidatorResult.success();
        };
    googleDecoder.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            issuerValidator, audienceValidator));
    this.decoder = googleDecoder;
  }

  public GoogleIdentity verify(String idToken) {
    if (clientId.isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "GOOGLE_LOGIN_UNCONFIGURED",
          "Google login is not configured.");
    }
    try {
      Jwt jwt = decoder.decode(idToken);
      String subject = jwt.getSubject();
      String email = jwt.getClaimAsString("email");
      Boolean verified = jwt.getClaim("email_verified");
      String name = jwt.getClaimAsString("name");
      if (subject == null || email == null || !Boolean.TRUE.equals(verified)) {
        throw invalidToken();
      }
      return new GoogleIdentity(subject, email, name, true);
    } catch (org.springframework.security.oauth2.jwt.JwtException exception) {
      throw invalidToken();
    }
  }

  private ApiException invalidToken() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "AUTH_INVALID_GOOGLE_TOKEN", "Google sign-in failed.");
  }
}
