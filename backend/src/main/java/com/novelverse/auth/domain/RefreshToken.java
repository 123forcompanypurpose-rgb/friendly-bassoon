package com.novelverse.auth.domain;

import com.novelverse.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Column(
      name = "token_hash",
      nullable = false,
      unique = true,
      length = 64,
      columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "replaced_by")
  private Long replacedBy;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "ip_hash", length = 64, columnDefinition = "char(64)")
  private String ipHash;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected RefreshToken() {}

  public RefreshToken(
      UserAccount user,
      String tokenHash,
      UUID familyId,
      Instant expiresAt,
      String userAgent,
      String ipHash) {
    this.user = user;
    this.tokenHash = tokenHash;
    this.familyId = familyId;
    this.expiresAt = expiresAt;
    this.userAgent = userAgent;
    this.ipHash = ipHash;
  }

  public boolean isActiveAt(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  public void revoke(Instant now, Long replacementId) {
    revokedAt = now;
    replacedBy = replacementId;
  }

  public void revoke(Instant now) {
    revoke(now, null);
  }

  public Long getId() {
    return id;
  }

  public UserAccount getUser() {
    return user;
  }

  public UUID getFamilyId() {
    return familyId;
  }

  public Long getReplacedBy() {
    return replacedBy;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
