package com.novelverse.auth.service;

import com.novelverse.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

  private static final String ACCESS_COOKIE = "access_token";
  private static final String REFRESH_COOKIE = "refresh_token";
  private static final String SESSION_COOKIE = "session_present";

  private final JwtService jwtService;
  private final boolean secure;
  private final String domain;

  public AuthCookieService(
      JwtService jwtService,
      @Value("${app.cookie-secure}") boolean secure,
      @Value("${app.cookie-domain:}") String domain) {
    this.jwtService = jwtService;
    this.secure = secure;
    this.domain = domain;
  }

  public void addSessionCookies(
      HttpServletResponse response, AuthService.SessionCredentials credentials) {
    addCookie(response, ACCESS_COOKIE, credentials.accessToken(), jwtService.getAccessTtl(), "/");
    addCookie(response, REFRESH_COOKIE, credentials.refreshToken(), credentials.refreshTtl(), "/");
    addReadableCookie(response, SESSION_COOKIE, "1", credentials.refreshTtl(), "/");
  }

  public void clearSessionCookies(HttpServletResponse response) {
    addCookie(response, ACCESS_COOKIE, "", Duration.ZERO, "/");
    addCookie(response, REFRESH_COOKIE, "", Duration.ZERO, "/");
    addReadableCookie(response, SESSION_COOKIE, "", Duration.ZERO, "/");
  }

  private void addCookie(
      HttpServletResponse response, String name, String value, Duration maxAge, String path) {
    addCookie(response, name, value, maxAge, path, true);
  }

  private void addReadableCookie(
      HttpServletResponse response, String name, String value, Duration maxAge, String path) {
    addCookie(response, name, value, maxAge, path, false);
  }

  private void addCookie(
      HttpServletResponse response,
      String name,
      String value,
      Duration maxAge,
      String path,
      boolean httpOnly) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(name, value)
            .httpOnly(httpOnly)
            .secure(secure)
            .sameSite("Lax")
            .path(path)
            .maxAge(maxAge);
    if (domain != null && !domain.isBlank()) {
      builder.domain(domain);
    }
    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }
}
