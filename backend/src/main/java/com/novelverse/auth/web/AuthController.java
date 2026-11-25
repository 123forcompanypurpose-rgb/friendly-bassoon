package com.novelverse.auth.web;

import com.novelverse.auth.service.AuthCookieService;
import com.novelverse.auth.service.AuthService;
import com.novelverse.auth.service.GoogleTokenVerifier;
import com.novelverse.common.ApiException;
import com.novelverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

  private final AuthService authService;
  private final AuthCookieService cookies;
  private final GoogleTokenVerifier googleTokenVerifier;

  public AuthController(
      AuthService authService, AuthCookieService cookies, GoogleTokenVerifier googleTokenVerifier) {
    this.authService = authService;
    this.cookies = cookies;
    this.googleTokenVerifier = googleTokenVerifier;
  }

  @PostMapping("/register")
  @Operation(summary = "Register a reader account and send an email verification link")
  public ResponseEntity<AuthResponses.MessageResponse> register(
      @Valid @RequestBody AuthRequests.RegisterRequest request) {
    authService.register(
        request.email(),
        request.username(),
        request.password(),
        request.displayName(),
        request.acceptedTerms());
    return ResponseEntity.accepted()
        .body(
            new AuthResponses.MessageResponse(
                "If the account can be created, a verification email will be sent."));
  }

  @PostMapping("/verify-email")
  @Operation(summary = "Verify an email address using its one-time link")
  public ResponseEntity<Void> verifyEmail(@Valid @RequestBody AuthRequests.TokenRequest request) {
    authService.verifyEmail(request.token());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/resend-verification")
  @Operation(summary = "Resend an email verification link")
  public AuthResponses.MessageResponse resendVerification(
      @Valid @RequestBody AuthRequests.EmailRequest request) {
    authService.resendVerification(request.email());
    return new AuthResponses.MessageResponse(
        "If the account needs verification, an email will be sent.");
  }

  @PostMapping("/login")
  @Operation(summary = "Sign in with an email address or username")
  public AuthResponses.SessionResponse login(
      @Valid @RequestBody AuthRequests.LoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    AuthService.SessionCredentials credentials =
        authService.login(
            request.identifier(),
            request.password(),
            servletRequest.getRemoteAddr(),
            servletRequest.getHeader("User-Agent"));
    cookies.addSessionCookies(servletResponse, credentials);
    return new AuthResponses.SessionResponse(AuthResponses.UserSummary.from(credentials.user()));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate the refresh token and issue a new access token")
  public AuthResponses.SessionResponse refresh(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    try {
      AuthService.SessionCredentials credentials =
          authService.refresh(
              refreshToken, servletRequest.getHeader("User-Agent"), servletRequest.getRemoteAddr());
      cookies.addSessionCookies(servletResponse, credentials);
      return new AuthResponses.SessionResponse(AuthResponses.UserSummary.from(credentials.user()));
    } catch (ApiException exception) {
      if (exception.getCode().startsWith("AUTH_INVALID_REFRESH")
          || exception.getCode().equals("AUTH_REFRESH_REUSE")) {
        cookies.clearSessionCookies(servletResponse);
      }
      throw exception;
    }
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke the current refresh token and clear authentication cookies")
  public ResponseEntity<Void> logout(
      @CookieValue(name = "refresh_token", required = false) String refreshToken,
      HttpServletResponse response) {
    authService.logout(refreshToken);
    cookies.clearSessionCookies(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/logout-all")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Revoke every refresh token belonging to the current account")
  public ResponseEntity<Void> logoutAll(
      @AuthenticationPrincipal UserPrincipal principal, HttpServletResponse response) {
    authService.logoutAll(principal.id());
    cookies.clearSessionCookies(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/forgot-password")
  @Operation(summary = "Send a one-time password reset link")
  public ResponseEntity<AuthResponses.MessageResponse> forgotPassword(
      @Valid @RequestBody AuthRequests.EmailRequest request) {
    authService.requestPasswordReset(request.email());
    return ResponseEntity.accepted()
        .body(
            new AuthResponses.MessageResponse(
                "If the account exists, a reset email will be sent."));
  }

  @PostMapping("/reset-password")
  @Operation(summary = "Set a new password with a one-time reset token")
  public ResponseEntity<Void> resetPassword(
      @Valid @RequestBody AuthRequests.ResetPasswordRequest request, HttpServletResponse response) {
    authService.resetPassword(request.token(), request.newPassword());
    cookies.clearSessionCookies(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/change-password")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Change the current account password and revoke sessions")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody AuthRequests.ChangePasswordRequest request,
      HttpServletResponse response) {
    authService.changePassword(principal.id(), request.currentPassword(), request.newPassword());
    cookies.clearSessionCookies(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/google")
  @Operation(summary = "Sign in or register with a verified Google ID token")
  public AuthResponses.SessionResponse google(
      @Valid @RequestBody AuthRequests.GoogleLoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    AuthService.SessionCredentials credentials =
        authService.googleLogin(
            googleTokenVerifier.verify(request.idToken()),
            request.acceptedTerms(),
            servletRequest.getHeader("User-Agent"),
            servletRequest.getRemoteAddr());
    cookies.addSessionCookies(servletResponse, credentials);
    return new AuthResponses.SessionResponse(AuthResponses.UserSummary.from(credentials.user()));
  }
}
