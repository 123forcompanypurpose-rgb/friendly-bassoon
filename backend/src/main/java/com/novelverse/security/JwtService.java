package com.novelverse.security;

import com.novelverse.user.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final String issuer;
  private final Duration accessTtl;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.issuer}") String issuer,
      @Value("${app.jwt.access-ttl}") Duration accessTtl) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT_SECRET must be set to a random Base64-encoded key.");
    }
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(secret);
    } catch (IllegalArgumentException exception) {
      keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    }
    if (keyBytes.length < 64) {
      throw new IllegalStateException("JWT_SECRET must contain at least 512 bits for HS512.");
    }
    this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    this.issuer = issuer;
    this.accessTtl = accessTtl;
  }

  public String createAccessToken(UserAccount user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(issuer)
        .subject(user.getId().toString())
        .claim("username", user.getUsername())
        .claim("role", user.getRole().name())
        .issuedAt(java.util.Date.from(now))
        .expiration(java.util.Date.from(now.plus(accessTtl)))
        .signWith(signingKey, Jwts.SIG.HS512)
        .compact();
  }

  public Claims parseAccessToken(String token) throws JwtException {
    Jws<Claims> signed =
        Jwts.parser().verifyWith(signingKey).requireIssuer(issuer).build().parseSignedClaims(token);
    return signed.getPayload();
  }

  public Duration getAccessTtl() {
    return accessTtl;
  }

  public static String hashOpaqueToken(String token) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  public static String randomOpaqueToken() {
    byte[] token = new byte[32];
    new java.security.SecureRandom().nextBytes(token);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
  }
}
