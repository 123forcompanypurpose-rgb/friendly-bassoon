package com.novelverse.user.service;

import com.novelverse.auth.domain.EmailTokenRepository;
import com.novelverse.auth.domain.EmailTokenType;
import com.novelverse.auth.domain.RefreshTokenRepository;
import com.novelverse.common.ApiException;
import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRepository;
import com.novelverse.user.domain.UserStatus;
import com.novelverse.user.web.ProfileRequests;
import com.novelverse.user.web.ProfileResponses;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final Set<String> ALLOWED_SOCIAL_LINKS =
      Set.of("website", "instagram", "tiktok", "twitter", "youtube");

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final EmailTokenRepository emailTokenRepository;
  private final JdbcTemplate jdbcTemplate;

  public UserService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      EmailTokenRepository emailTokenRepository,
      JdbcTemplate jdbcTemplate) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.emailTokenRepository = emailTokenRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public ProfileResponses.MyProfile getMyProfile(Long userId) {
    UserAccount user = findActiveById(userId);
    return ProfileResponses.MyProfile.from(user, followerCount(userId), followingCount(userId));
  }

  @Transactional
  public ProfileResponses.MyProfile updateMyProfile(
      Long userId, ProfileRequests.UpdateProfileRequest request) {
    UserAccount user = findActiveById(userId);
    if (request.displayName() != null && request.displayName().isBlank()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "PROFILE_DISPLAY_NAME_REQUIRED",
          "Display name must not be blank.");
    }
    String displayName =
        request.displayName() == null ? user.getDisplayName() : request.displayName().trim();
    String bio = request.bio() == null ? user.getBio() : request.bio().trim();
    Map<String, String> socialLinks =
        request.socialLinks() == null
            ? user.getSocialLinks()
            : validateSocialLinks(request.socialLinks());
    LocalDate birthDate = request.birthDate() == null ? user.getBirthDate() : request.birthDate();
    boolean showMature = request.showMature() == null ? user.isShowMature() : request.showMature();
    if (showMature && (birthDate == null || birthDate.isAfter(LocalDate.now().minusYears(18)))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "PROFILE_AGE_GATE_REQUIRED",
          "An adult birth date is required for mature content.");
    }
    user.updateProfile(displayName, bio, user.getAvatarUrl(), socialLinks, birthDate, showMature);
    return ProfileResponses.MyProfile.from(user, followerCount(userId), followingCount(userId));
  }

  @Transactional(readOnly = true)
  public ProfileResponses.PublicProfile getPublicProfile(String username) {
    UserAccount user =
        userRepository
            .findByUsernameIgnoreCaseAndDeletedAtIsNull(username)
            .filter(account -> account.getStatus() != UserStatus.DELETED)
            .orElseThrow(this::userNotFound);
    return ProfileResponses.PublicProfile.from(
        user, followerCount(user.getId()), followingCount(user.getId()));
  }

  @Transactional
  public void follow(Long followerId, String username) {
    UserAccount target = findActiveByUsername(username);
    ensureDifferentUser(followerId, target.getId());
    if (isBlockedEitherWay(followerId, target.getId())) {
      throw new ApiException(
          HttpStatus.CONFLICT, "USER_INTERACTION_BLOCKED", "This follow cannot be completed.");
    }
    jdbcTemplate.update(
        """
                insert into follows(follower_id, followee_id)
                values (?, ?)
                on conflict (follower_id, followee_id) do nothing
                """,
        followerId,
        target.getId());
  }

  @Transactional
  public void unfollow(Long followerId, String username) {
    UserAccount target = findActiveByUsername(username);
    ensureDifferentUser(followerId, target.getId());
    jdbcTemplate.update(
        "delete from follows where follower_id = ? and followee_id = ?",
        followerId,
        target.getId());
  }

  @Transactional
  public void block(Long blockerId, String username) {
    UserAccount target = findActiveByUsername(username);
    ensureDifferentUser(blockerId, target.getId());
    jdbcTemplate.update(
        """
                insert into blocks(blocker_id, blocked_id)
                values (?, ?)
                on conflict (blocker_id, blocked_id) do nothing
                """,
        blockerId,
        target.getId());
    jdbcTemplate.update(
        """
                delete from follows
                where (follower_id = ? and followee_id = ?)
                   or (follower_id = ? and followee_id = ?)
                """,
        blockerId,
        target.getId(),
        target.getId(),
        blockerId);
  }

  @Transactional
  public void unblock(Long blockerId, String username) {
    UserAccount target = findActiveByUsername(username);
    ensureDifferentUser(blockerId, target.getId());
    jdbcTemplate.update(
        "delete from blocks where blocker_id = ? and blocked_id = ?", blockerId, target.getId());
  }

  @Transactional
  public void deleteMyAccount(Long userId) {
    UserAccount user = findActiveById(userId);
    Instant now = Instant.now();
    refreshTokenRepository
        .findByUserIdAndRevokedAtIsNull(userId)
        .forEach(token -> token.revoke(now));
    emailTokenRepository.consumeOutstanding(userId, EmailTokenType.VERIFY_EMAIL);
    emailTokenRepository.consumeOutstanding(userId, EmailTokenType.RESET_PASSWORD);
    jdbcTemplate.update(
        "delete from follows where follower_id = ? or followee_id = ?", userId, userId);
    jdbcTemplate.update(
        "delete from blocks where blocker_id = ? or blocked_id = ?", userId, userId);
    user.markDeleted(now);
  }

  @Transactional
  public String updateAvatar(Long userId, String avatarUrl) {
    UserAccount user = findActiveById(userId);
    user.changeAvatar(avatarUrl);
    return avatarUrl;
  }

  private Map<String, String> validateSocialLinks(Map<String, String> socialLinks) {
    if (socialLinks.size() > 5) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "PROFILE_INVALID_SOCIAL_LINKS",
          "At most five social links are allowed.");
    }
    Map<String, String> safeLinks = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : socialLinks.entrySet()) {
      String key = entry.getKey().toLowerCase(Locale.ROOT);
      String value = entry.getValue();
      if (!ALLOWED_SOCIAL_LINKS.contains(key) || value == null || value.length() > 500) {
        throw invalidSocialLinks();
      }
      URI uri;
      try {
        uri = URI.create(value);
      } catch (IllegalArgumentException exception) {
        throw invalidSocialLinks();
      }
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null) {
        throw invalidSocialLinks();
      }
      safeLinks.put(key, uri.toString());
    }
    return safeLinks;
  }

  private boolean isBlockedEitherWay(Long first, Long second) {
    Long matches =
        jdbcTemplate.queryForObject(
            """
                select count(*) from blocks
                where (blocker_id = ? and blocked_id = ?)
                   or (blocker_id = ? and blocked_id = ?)
                """,
            Long.class,
            first,
            second,
            second,
            first);
    return matches != null && matches > 0L;
  }

  private long followerCount(Long userId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from follows where followee_id = ?", Long.class, userId);
    return count == null ? 0 : count;
  }

  private long followingCount(Long userId) {
    Long count =
        jdbcTemplate.queryForObject(
            "select count(*) from follows where follower_id = ?", Long.class, userId);
    return count == null ? 0 : count;
  }

  private UserAccount findActiveById(Long userId) {
    return userRepository
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .orElseThrow(this::userNotFound);
  }

  private UserAccount findActiveByUsername(String username) {
    return userRepository
        .findByUsernameIgnoreCaseAndDeletedAtIsNull(username)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .orElseThrow(this::userNotFound);
  }

  private void ensureDifferentUser(Long first, Long second) {
    if (first.equals(second)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "USER_SELF_ACTION",
          "You cannot perform this action on yourself.");
    }
  }

  private ApiException userNotFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "The requested user was not found.");
  }

  private ApiException invalidSocialLinks() {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "PROFILE_INVALID_SOCIAL_LINKS",
        "Social links must use HTTPS and a supported profile type.");
  }
}
