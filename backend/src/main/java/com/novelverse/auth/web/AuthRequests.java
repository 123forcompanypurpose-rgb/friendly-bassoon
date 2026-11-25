package com.novelverse.auth.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

  private AuthRequests() {}

  public record RegisterRequest(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Pattern(regexp = "^[a-z0-9_]{3,20}$") String username,
      @NotBlank @Size(min = 8, max = 72) String password,
      @Size(max = 60) String displayName,
      @AssertTrue boolean acceptedTerms) {}

  public record LoginRequest(
      @NotBlank @Size(max = 254) String identifier, @NotBlank @Size(max = 72) String password) {}

  public record TokenRequest(@NotBlank @Size(max = 256) String token) {}

  public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}

  public record ResetPasswordRequest(
      @NotBlank @Size(max = 256) String token,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record ChangePasswordRequest(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record GoogleLoginRequest(
      @NotBlank @Size(max = 8192) String idToken, boolean acceptedTerms) {}
}
