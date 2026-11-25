package com.novelverse.user.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, columnDefinition = "citext")
  private String email;

  @Column(nullable = false, unique = true, columnDefinition = "citext")
  private String username;

  @JsonIgnore
  @Column(name = "password_hash", length = 100)
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 60)
  private String displayName;

  @Column(length = 500)
  private String bio;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "social_links", nullable = false, columnDefinition = "jsonb")
  private Map<String, String> socialLinks = new LinkedHashMap<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role = UserRole.USER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "suspended_until")
  private Instant suspendedUntil;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "terms_accepted_at")
  private Instant termsAcceptedAt;

  @Column(name = "google_sub", unique = true, length = 64)
  private String googleSub;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "show_mature", nullable = false)
  private boolean showMature;

  @Column(nullable = false, length = 10)
  private String locale = "en";

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 2, columnDefinition = "char(2)")
  private String country;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_seen_at")
  private Instant lastSeenAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserAccount() {}

  public UserAccount(String email, String username, String displayName, String passwordHash) {
    this.email = email;
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
  }

  @PrePersist
  void initializeSocialLinks() {
    if (socialLinks == null) {
      socialLinks = new LinkedHashMap<>();
    }
  }

  @PreUpdate
  void normalizeSocialLinks() {
    if (socialLinks == null) {
      socialLinks = new LinkedHashMap<>();
    }
  }

  public void verifyEmail() {
    emailVerified = true;
  }

  public void acceptTerms(Instant acceptedAt) {
    termsAcceptedAt = acceptedAt;
  }

  public void linkGoogleAccount(String subject) {
    googleSub = subject;
    emailVerified = true;
  }

  public void updateProfile(
      String displayName,
      String bio,
      String avatarUrl,
      Map<String, String> socialLinks,
      LocalDate birthDate,
      boolean showMature) {
    this.displayName = displayName;
    this.bio = bio;
    this.avatarUrl = avatarUrl;
    this.socialLinks = new LinkedHashMap<>(socialLinks);
    this.birthDate = birthDate;
    this.showMature = showMature;
  }

  public void changePassword(String encodedPassword) {
    passwordHash = encodedPassword;
  }

  public void changeAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public void recordSuccessfulLogin() {
    failedLoginCount = 0;
    lockedUntil = null;
    lastSeenAt = Instant.now();
  }

  public void markDeleted(Instant now) {
    email = "deleted+" + id + "@deleted.invalid";
    username = "deleted_" + id;
    displayName = "Deleted user";
    bio = null;
    avatarUrl = null;
    socialLinks = new LinkedHashMap<>();
    passwordHash = null;
    googleSub = null;
    emailVerified = false;
    status = UserStatus.DELETED;
    deletedAt = now;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getBio() {
    return bio;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public Map<String, String> getSocialLinks() {
    return socialLinks == null ? Map.of() : Map.copyOf(socialLinks);
  }

  public UserRole getRole() {
    return role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getSuspendedUntil() {
    return suspendedUntil;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public Instant getTermsAcceptedAt() {
    return termsAcceptedAt;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public boolean isShowMature() {
    return showMature;
  }

  public String getLocale() {
    return locale;
  }

  public String getCountry() {
    return country;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
