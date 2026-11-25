package com.novelverse.user.web;

import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public final class ProfileResponses {

  private ProfileResponses() {}

  public record MyProfile(
      Long id,
      String email,
      String username,
      String displayName,
      String bio,
      String avatarUrl,
      Map<String, String> socialLinks,
      LocalDate birthDate,
      boolean showMature,
      boolean emailVerified,
      UserRole role,
      long followersCount,
      long followingCount,
      Instant createdAt) {

    public static MyProfile from(UserAccount user, long followers, long following) {
      return new MyProfile(
          user.getId(),
          user.getEmail(),
          user.getUsername(),
          user.getDisplayName(),
          user.getBio(),
          user.getAvatarUrl(),
          user.getSocialLinks(),
          user.getBirthDate(),
          user.isShowMature(),
          user.isEmailVerified(),
          user.getRole(),
          followers,
          following,
          user.getCreatedAt());
    }
  }

  public record PublicProfile(
      Long id,
      String username,
      String displayName,
      String bio,
      String avatarUrl,
      Map<String, String> socialLinks,
      long followersCount,
      long followingCount,
      Instant createdAt) {

    public static PublicProfile from(UserAccount user, long followers, long following) {
      return new PublicProfile(
          user.getId(),
          user.getUsername(),
          user.getDisplayName(),
          user.getBio(),
          user.getAvatarUrl(),
          user.getSocialLinks(),
          followers,
          following,
          user.getCreatedAt());
    }
  }
}
