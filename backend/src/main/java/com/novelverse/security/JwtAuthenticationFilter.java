package com.novelverse.security;

import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRepository;
import com.novelverse.user.domain.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String ACCESS_COOKIE = "access_token";

  private final JwtService jwtService;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    accessToken(request).ifPresent(this::authenticate);
    filterChain.doFilter(request, response);
  }

  private Optional<String> accessToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> ACCESS_COOKIE.equals(cookie.getName()))
        .map(Cookie::getValue)
        .findFirst();
  }

  private void authenticate(String token) {
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      return;
    }
    try {
      Claims claims = jwtService.parseAccessToken(token);
      Long userId = Long.valueOf(claims.getSubject());
      userRepository
          .findById(userId)
          .filter(this::isAllowed)
          .ifPresent(user -> setAuthentication(user));
    } catch (JwtException | IllegalArgumentException ignored) {
      SecurityContextHolder.clearContext();
    }
  }

  private boolean isAllowed(UserAccount user) {
    return user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null;
  }

  private void setAuthentication(UserAccount user) {
    UserPrincipal principal = new UserPrincipal(user.getId(), user.getUsername(), user.getRole());
    List<SimpleGrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
  }
}
