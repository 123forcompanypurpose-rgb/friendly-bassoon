package com.novelverse.auth.service;

import com.novelverse.auth.domain.EmailToken;
import com.novelverse.auth.domain.EmailTokenRepository;
import com.novelverse.auth.domain.EmailTokenType;
import com.novelverse.auth.domain.RefreshToken;
import com.novelverse.auth.domain.RefreshTokenRepository;
import com.novelverse.common.ApiException;
import com.novelverse.security.IpHashService;
import com.novelverse.security.JwtService;
import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRepository;
import com.novelverse.user.domain.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  public record SessionCredentials(
      String accessToken, String refreshToken, Duration refreshTtl, UserAccount user) {}

  private static final String INVALID_CREDENTIALS =
      "The supplied credentials are invalid or the account is unavailable.";

  private final UserRepository userRepository;
  private final EmailTokenRepository emailTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final JwtService jwtService;
  private final AccountLockoutService lockoutService;
  private final EmailService emailService;
  private final IpHashService ipHashService;
  private final Duration refreshTtl;
  private final String dummyPasswordHash;

  public AuthService(
      UserRepository userRepository,
      EmailTokenRepository emailTokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      JwtService jwtService,
      AccountLockoutService lockoutService,
      EmailService emailService,
      IpHashService ipHashService,
      @Value("${app.jwt.refresh-ttl}") Duration refreshTtl) {
    this.userRepository = userRepository;
    this.emailTokenRepository = emailTokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.jwtService = jwtService;
    this.lockoutService = lockoutService;
    this.emailService = emailService;
    this.ipHashService = ipHashService;
    this.refreshTtl = refreshTtl;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public void register(
      String email, String username, String password, String displayName, boolean acceptedTerms) {
    if (!acceptedTerms) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "TERMS_ACCEPTANCE_REQUIRED",
          "Accept the terms to create an account.");
    }
    String normalizedEmail = normalizeEmail(email);
    if (userRepository.existsByEmailIgnoreCase(normalizedEmail)
        || userRepository.existsByUsernameIgnoreCase(username)) {
      return;
    }
    validatePassword(password);
    String safeDisplayName =
        displayName == null || displayName.isBlank() ? username : displayName.trim();
    UserAccount user =
        new UserAccount(
            normalizedEmail, username, safeDisplayName, passwordEncoder.encode(password));
    if (acceptedTerms) {
      user.acceptTerms(Instant.now());
    }
    userRepository.saveAndFlush(user);
    sendEmailToken(user, EmailTokenType.VERIFY_EMAIL, Duration.ofHours(24));
  }

  @Transactional
  public void verifyEmail(String rawToken) {
    EmailToken token = findUsableToken(rawToken, EmailTokenType.VERIFY_EMAIL);
    token.getUser().verifyEmail();
    token.markUsed(Instant.now());
  }

  @Transactional
  public void resendVerification(String email) {
    userRepository
        .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(email))
        .filter(user -> !user.isEmailVerified() && user.getStatus() == UserStatus.ACTIVE)
        .ifPresent(user -> sendEmailToken(user, EmailTokenType.VERIFY_EMAIL, Duration.ofHours(24)));
  }

  @Transactional(noRollbackFor = ApiException.class)
  public SessionCredentials login(
      String identifier, String password, String remoteAddress, String userAgent) {
    String normalizedIdentifier = identifier.trim().toLowerCase(Locale.ROOT);
    UserAccount user = findUserByIdentifier(normalizedIdentifier);
    String accountKey = user == null ? "unknown:" + normalizedIdentifier : "user:" + user.getId();
    String identityHash = ipHashService.hash(accountKey);
    String ipHash = ipHashService.hash(normalizeAddress(remoteAddress));
    Instant now = Instant.now();

    if (lockoutService.isLocked(identityHash, ipHash, now)) {
      throw invalidCredentials();
    }

    boolean passwordMatches =
        user != null
            && user.getPasswordHash() != null
            && passwordEncoder.matches(password, user.getPasswordHash());
    if (user == null || !passwordMatches || user.getStatus() != UserStatus.ACTIVE) {
      if (user == null || user.getStatus() == UserStatus.ACTIVE) {
        lockoutService.recordFailure(identityHash, ipHash, now);
      }
      if (user == null || user.getPasswordHash() == null) {
        passwordEncoder.matches(password, dummyPasswordHash);
      }
      throw invalidCredentials();
    }

    lockoutService.clear(identityHash, ipHash);
    user.recordSuccessfulLogin();
    return createSession(user, UUID.randomUUID(), userAgent, ipHash);
  }

  @Transactional(noRollbackFor = ApiException.class)
  public SessionCredentials refresh(
      String rawRefreshToken, String userAgent, String remoteAddress) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw invalidRefreshToken();
    }
    String tokenHash = JwtService.hashOpaqueToken(rawRefreshToken);
    RefreshToken current =
        refreshTokenRepository.findForUpdate(tokenHash).orElseThrow(this::invalidRefreshToken);
    Instant now = Instant.now();

    if (current.getRevokedAt() != null) {
      if (current.getReplacedBy() != null) {
        revokeFamily(current.getFamilyId(), now);
        throw new ApiException(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REFRESH_REUSE",
            "This session has expired. Please sign in again.");
      }
      throw invalidRefreshToken();
    }
    if (!current.getExpiresAt().isAfter(now)) {
      current.revoke(now);
      throw invalidRefreshToken();
    }

    UserAccount user = current.getUser();
    if (user.getStatus() != UserStatus.ACTIVE) {
      revokeFamily(current.getFamilyId(), now);
      throw invalidRefreshToken();
    }
    String ipHash = ipHashService.hash(normalizeAddress(remoteAddress));
    SessionCredentials replacement = createSession(user, current.getFamilyId(), userAgent, ipHash);
    RefreshToken newToken =
        refreshTokenRepository
            .findForUpdate(JwtService.hashOpaqueToken(replacement.refreshToken()))
            .orElseThrow(() -> new IllegalStateException("New refresh token was not persisted."));
    current.revoke(now, newToken.getId());
    return replacement;
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }
    refreshTokenRepository
        .findForUpdate(JwtService.hashOpaqueToken(rawRefreshToken))
        .filter(token -> token.getRevokedAt() == null)
        .ifPresent(token -> token.revoke(Instant.now()));
  }

  @Transactional
  public void logoutAll(Long userId) {
    Instant now = Instant.now();
    refreshTokenRepository
        .findByUserIdAndRevokedAtIsNull(userId)
        .forEach(token -> token.revoke(now));
  }

  @Transactional
  public void requestPasswordReset(String email) {
    userRepository
        .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(email))
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .ifPresent(
            user -> sendEmailToken(user, EmailTokenType.RESET_PASSWORD, Duration.ofHours(1)));
  }

  @Transactional
  public void resetPassword(String rawToken, String newPassword) {
    validatePassword(newPassword);
    EmailToken token = findUsableToken(rawToken, EmailTokenType.RESET_PASSWORD);
    UserAccount user = token.getUser();
    user.changePassword(passwordEncoder.encode(newPassword));
    token.markUsed(Instant.now());
    revokeAllRefreshTokens(user.getId());
  }

  @Transactional
  public void changePassword(Long userId, String currentPassword, String newPassword) {
    UserAccount user = userRepository.findById(userId).orElseThrow(this::invalidCredentials);
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "AUTH_CURRENT_PASSWORD_INVALID",
          "Current password is incorrect.");
    }
    validatePassword(newPassword);
    user.changePassword(passwordEncoder.encode(newPassword));
    revokeAllRefreshTokens(userId);
  }

  @Transactional
  public SessionCredentials googleLogin(
      GoogleIdentity identity, boolean acceptedTerms, String userAgent, String remoteAddress) {
    String email = normalizeEmail(identity.email());
    UserAccount user =
        userRepository.findByGoogleSubAndDeletedAtIsNull(identity.subject()).orElse(null);

    if (user == null) {
      user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElse(null);
      if (user != null) {
        if (user.getGoogleSub() != null && !user.getGoogleSub().equals(identity.subject())) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "AUTH_GOOGLE_ACCOUNT_LINKED",
              "This email is linked to another Google account.");
        }
        user.linkGoogleAccount(identity.subject());
      } else {
        if (!acceptedTerms) {
          throw new ApiException(
              HttpStatus.BAD_REQUEST,
              "TERMS_ACCEPTANCE_REQUIRED",
              "Accept the terms to create an account.");
        }
        String username = uniqueUsername(email);
        String displayName =
            identity.name() == null || identity.name().isBlank()
                ? username
                : identity.name().trim();
        user = new UserAccount(email, username, displayName, null);
        user.linkGoogleAccount(identity.subject());
        user.acceptTerms(Instant.now());
      }
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw invalidCredentials();
    }
    user.recordSuccessfulLogin();
    userRepository.save(user);
    String ipHash = ipHashService.hash(normalizeAddress(remoteAddress));
    return createSession(user, UUID.randomUUID(), userAgent, ipHash);
  }

  private void sendEmailToken(UserAccount user, EmailTokenType type, Duration ttl) {
    emailTokenRepository.consumeOutstanding(user.getId(), type);
    String rawToken = JwtService.randomOpaqueToken();
    EmailToken emailToken =
        new EmailToken(user, type, JwtService.hashOpaqueToken(rawToken), Instant.now().plus(ttl));
    emailTokenRepository.save(emailToken);
    if (type == EmailTokenType.VERIFY_EMAIL) {
      emailService.sendVerification(user.getEmail(), rawToken);
    } else {
      emailService.sendPasswordReset(user.getEmail(), rawToken);
    }
  }

  private EmailToken findUsableToken(String rawToken, EmailTokenType type) {
    if (rawToken == null || rawToken.isBlank()) {
      throw invalidEmailToken();
    }
    EmailToken token =
        emailTokenRepository
            .findForUse(JwtService.hashOpaqueToken(rawToken), type)
            .orElseThrow(this::invalidEmailToken);
    if (!token.isUsableAt(Instant.now()) || token.getUser().getStatus() != UserStatus.ACTIVE) {
      throw invalidEmailToken();
    }
    return token;
  }

  private SessionCredentials createSession(
      UserAccount user, UUID familyId, String userAgent, String ipHash) {
    String refreshTokenValue = JwtService.randomOpaqueToken();
    RefreshToken refreshToken =
        new RefreshToken(
            user,
            JwtService.hashOpaqueToken(refreshTokenValue),
            familyId,
            Instant.now().plus(refreshTtl),
            truncate(userAgent, 255),
            ipHash);
    refreshTokenRepository.saveAndFlush(refreshToken);
    return new SessionCredentials(
        jwtService.createAccessToken(user), refreshTokenValue, refreshTtl, user);
  }

  private void revokeFamily(UUID familyId, Instant now) {
    refreshTokenRepository
        .findByFamilyIdAndRevokedAtIsNull(familyId)
        .forEach(token -> token.revoke(now));
  }

  private void revokeAllRefreshTokens(Long userId) {
    Instant now = Instant.now();
    refreshTokenRepository
        .findByUserIdAndRevokedAtIsNull(userId)
        .forEach(token -> token.revoke(now));
  }

  private UserAccount findUserByIdentifier(String identifier) {
    if (identifier.contains("@")) {
      return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identifier).orElse(null);
    }
    return userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(identifier).orElse(null);
  }

  private String uniqueUsername(String email) {
    String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
    String base = localPart.replaceAll("[^a-z0-9_]", "_");
    if (base.length() < 3) {
      base = "reader";
    }
    base = base.substring(0, Math.min(16, base.length()));
    for (int attempt = 0; attempt < 100; attempt++) {
      String suffix = attempt == 0 ? "" : "_" + Integer.toString(attempt, 36);
      String candidate = base.substring(0, Math.min(base.length(), 20 - suffix.length())) + suffix;
      if (!userRepository.existsByUsernameIgnoreCase(candidate)) {
        return candidate;
      }
    }
    throw new ApiException(
        HttpStatus.CONFLICT, "AUTH_USERNAME_UNAVAILABLE", "Unable to create a unique username.");
  }

  private void validatePassword(String password) {
    try {
      passwordPolicy.validate(password);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "AUTH_WEAK_PASSWORD", exception.getMessage());
    }
  }

  private ApiException invalidCredentials() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", INVALID_CREDENTIALS);
  }

  private ApiException invalidRefreshToken() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED,
        "AUTH_INVALID_REFRESH_TOKEN",
        "This session has expired. Please sign in again.");
  }

  private ApiException invalidEmailToken() {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "AUTH_INVALID_EMAIL_TOKEN", "This link is invalid or has expired.");
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String normalizeAddress(String remoteAddress) {
    return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
  }

  private String truncate(String value, int maxLength) {
    return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
  }
}
