package com.novelverse.security;

import com.novelverse.common.ApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ApiErrorWriter errorWriter;

  public ApiAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    errorWriter.write(
        response,
        HttpStatus.UNAUTHORIZED,
        "AUTHENTICATION_REQUIRED",
        "Authentication is required.",
        request.getRequestURI(),
        List.of());
  }
}
