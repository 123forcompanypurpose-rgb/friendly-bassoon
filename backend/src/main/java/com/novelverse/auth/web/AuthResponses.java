package com.novelverse.auth.web;

import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRole;

public final class AuthResponses {

  private AuthResponses() {}

  public record MessageResponse(String message) {}

  public record UserSummary(
      Long id,
      String username,
      String email,
      String displayName,
      boolean emailVerified,
      UserRole role) {
    public static UserSummary from(UserAccount user) {
      return new UserSummary(
          user.getId(),
          user.getUsername(),
          user.getEmail(),
          user.getDisplayName(),
          user.isEmailVerified(),
          user.getRole());
    }
  }

  public record SessionResponse(UserSummary user) {}
}
