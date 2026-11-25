package com.novelverse.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.novelverse.auth.service.AuthCookieService;
import com.novelverse.auth.service.AuthService.SessionCredentials;
import com.novelverse.security.JwtService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

  @Test
  void sessionCookiesUseRootPathSoFrontendMiddlewareCanSeeTheSession() {
    JwtService jwtService = mock(JwtService.class);
    when(jwtService.getAccessTtl()).thenReturn(Duration.ofMinutes(15));
    AuthCookieService service = new AuthCookieService(jwtService, false, "");
    MockHttpServletResponse response = new MockHttpServletResponse();

    service.addSessionCookies(
        response,
        new SessionCredentials("access-value", "refresh-value", Duration.ofDays(30), null));

    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .anySatisfy(
            header ->
                assertThat(header).contains("access_token=access-value", "Path=/;", "HttpOnly"));
    assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
        .anySatisfy(
            header ->
                assertThat(header).contains("refresh_token=refresh-value", "Path=/;", "HttpOnly"));
  }
}
