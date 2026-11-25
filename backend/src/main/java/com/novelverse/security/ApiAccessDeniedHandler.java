package com.novelverse.security;

import com.novelverse.common.ApiErrorWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

  private final ApiErrorWriter errorWriter;

  public ApiAccessDeniedHandler(ApiErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException, ServletException {
    errorWriter.write(
        response,
        HttpStatus.FORBIDDEN,
        "ACCESS_DENIED",
        "You are not allowed to perform this action.",
        request.getRequestURI(),
        List.of());
  }
}
