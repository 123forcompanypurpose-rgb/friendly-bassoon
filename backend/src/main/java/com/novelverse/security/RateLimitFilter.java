package com.novelverse.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.novelverse.common.ApiErrorWriter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private record Limit(String category, long capacity, Duration period) {}

  private final Cache<String, Bucket> buckets =
      Caffeine.newBuilder().maximumSize(100_000).expireAfterAccess(2, TimeUnit.HOURS).build();
  private final IpHashService ipHashService;
  private final ApiErrorWriter errorWriter;

  public RateLimitFilter(IpHashService ipHashService, ApiErrorWriter errorWriter) {
    this.ipHashService = ipHashService;
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    if (!path.startsWith("/api/v1/")) {
      filterChain.doFilter(request, response);
      return;
    }

    String ipHash = ipHashService.hash(clientAddress(request));
    Limit limit = limitFor(path);
    String key = limit.category() + ":" + ipHash;
    Bucket bucket =
        buckets.get(
            key,
            ignored ->
                Bucket.builder()
                    .addLimit(
                        Bandwidth.classic(
                            limit.capacity(), Refill.intervally(limit.capacity(), limit.period())))
                    .build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (!probe.isConsumed()) {
      long retryAfter =
          Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
      response.setHeader("Retry-After", Long.toString(retryAfter));
      errorWriter.write(
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "RATE_LIMITED",
          "Too many requests. Please try again later.",
          path,
          java.util.List.of());
      return;
    }
    filterChain.doFilter(request, response);
  }

  private String clientAddress(HttpServletRequest request) {
    String address = request.getRemoteAddr();
    return address == null || address.isBlank() ? "unknown" : address;
  }

  private Limit limitFor(String path) {
    if (path.equals("/api/v1/auth/login")) {
      return new Limit("login", 5, Duration.ofMinutes(1));
    }
    if (path.equals("/api/v1/auth/google")) {
      return new Limit("google-login", 5, Duration.ofMinutes(1));
    }
    if (path.equals("/api/v1/auth/register")) {
      return new Limit("register", 5, Duration.ofHours(1));
    }
    if (path.equals("/api/v1/auth/forgot-password")
        || path.equals("/api/v1/auth/resend-verification")) {
      return new Limit("email-token", 3, Duration.ofHours(1));
    }
    if (path.equals("/api/v1/me/avatar/presign")) {
      return new Limit("avatar", 10, Duration.ofHours(1));
    }
    return new Limit("general", 300, Duration.ofMinutes(1));
  }
}
