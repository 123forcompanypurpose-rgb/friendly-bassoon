package com.novelverse.config;

import com.novelverse.security.ApiAccessDeniedHandler;
import com.novelverse.security.ApiAuthenticationEntryPoint;
import com.novelverse.security.CsrfHeaderFilter;
import com.novelverse.security.JwtAuthenticationFilter;
import com.novelverse.security.RateLimitFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_AUTH_POSTS = {
    "/api/v1/auth/register",
    "/api/v1/auth/verify-email",
    "/api/v1/auth/resend-verification",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/api/v1/auth/forgot-password",
    "/api/v1/auth/reset-password",
    "/api/v1/auth/google"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      CsrfHeaderFilter csrfHeaderFilter,
      RateLimitFilter rateLimitFilter,
      ApiAuthenticationEntryPoint authenticationEntryPoint,
      ApiAccessDeniedHandler accessDeniedHandler,
      @Value("${app.openapi.public-docs:false}") boolean publicDocs)
      throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .headers(
            headers ->
                headers
                    .contentTypeOptions(Customizer.withDefaults())
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        policy ->
                            policy.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy
                                    .STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
        .authorizeHttpRequests(
            authorize -> {
              authorize
                  .requestMatchers(HttpMethod.OPTIONS, "/**")
                  .permitAll()
                  .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/v1/home")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/v1/genres", "/api/v1/tags")
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/v1/stories", "/api/v1/stories/*")
                  .permitAll()
                  .requestMatchers(
                      HttpMethod.GET, "/api/v1/stories/*/chapters", "/api/v1/chapters/*")
                  .permitAll()
                  .requestMatchers(HttpMethod.POST, "/api/v1/chapters/*/view")
                  .permitAll()
                  .requestMatchers(PUBLIC_AUTH_POSTS)
                  .permitAll()
                  .requestMatchers(HttpMethod.GET, "/api/v1/users/*")
                  .permitAll();
              if (publicDocs) {
                authorize
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll();
              } else {
                authorize
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .hasRole("ADMIN");
              }
              authorize.requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll();
            })
        .addFilterBefore(
            jwtAuthenticationFilter,
            org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
                .class)
        .addFilterBefore(csrfHeaderFilter, JwtAuthenticationFilter.class)
        .addFilterBefore(rateLimitFilter, CsrfHeaderFilter.class);
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.web-origin}") String allowedOrigins) {
    CorsConfiguration cors = new CorsConfiguration();
    List<String> origins =
        new ArrayList<>(
            Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
    String codespaceName = System.getenv("CODESPACE_NAME");
    String forwardingDomain = System.getenv("GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN");
    if (codespaceName != null
        && !codespaceName.isBlank()
        && forwardingDomain != null
        && !forwardingDomain.isBlank()) {
      origins.add("https://" + codespaceName + "-3000." + forwardingDomain);
    }
    origins.add("http://localhost:3000");
    origins.add("http://127.0.0.1:3000");
    cors.setAllowedOrigins(origins);
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "X-Requested-With", "Accept"));
    cors.setExposedHeaders(List.of("Retry-After", "X-Request-Id"));
    cors.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }
}
