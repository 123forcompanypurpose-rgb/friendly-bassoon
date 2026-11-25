package com.novelverse.content.service;

import com.novelverse.common.ApiException;
import com.novelverse.content.domain.Story;
import com.novelverse.content.domain.StoryMaturity;
import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRepository;
import com.novelverse.user.domain.UserRole;
import com.novelverse.user.domain.UserStatus;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ContentAccess {

  private final UserRepository userRepository;

  public ContentAccess(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public UserAccount requireVerifiedAuthor(Long userId) {
    UserAccount user = userRepository.findById(userId).orElseThrow(this::notFound);
    if (user.getStatus() != UserStatus.ACTIVE || user.getDeletedAt() != null) {
      throw notFound();
    }
    if (!user.isEmailVerified()) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "EMAIL_VERIFICATION_REQUIRED",
          "Verify your email address before creating or editing stories.");
    }
    return user;
  }

  public boolean canReadMature(Long userId) {
    if (userId == null) {
      return false;
    }
    return userRepository
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null)
        .filter(UserAccount::isShowMature)
        .map(UserAccount::getBirthDate)
        .filter(birthDate -> !birthDate.isAfter(LocalDate.now().minusYears(18)))
        .isPresent();
  }

  public boolean canReadStory(Story story, Long userId) {
    return story.getMaturity() != StoryMaturity.MATURE || canReadMature(userId);
  }

  public boolean canModerate(Long userId) {
    if (userId == null) {
      return false;
    }
    return userRepository
        .findById(userId)
        .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null)
        .map(user -> user.getRole() == UserRole.MODERATOR || user.getRole() == UserRole.ADMIN)
        .orElse(false);
  }

  public ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "The requested content was not found.");
  }
}
