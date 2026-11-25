package com.novelverse.security;

import com.novelverse.common.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CsrfHeaderFilter extends OncePerRequestFilter {

  private static final String REQUIRED_HEADER = "X-Requested-With";
  private static final String REQUIRED_VALUE = "novelverse";
  private static final Set<String> SAFE_METHODS =
      Set.of(
          HttpMethod.GET.name(),
          HttpMethod.HEAD.name(),
          HttpMethod.OPTIONS.name(),
          HttpMethod.TRACE.name());

  private final ApiErrorWriter errorWriter;

  public CsrfHeaderFilter(ApiErrorWriter errorWriter) {
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!SAFE_METHODS.contains(request.getMethod())
        && !REQUIRED_VALUE.equals(request.getHeader(REQUIRED_HEADER))) {
      errorWriter.write(
          response,
          HttpStatus.FORBIDDEN,
          "CSRF_HEADER_REQUIRED",
          "The required request header is missing.",
          request.getRequestURI(),
          java.util.List.of());
      return;
    }
    filterChain.doFilter(request, response);
  }
}
