package com.novelverse.auth.domain;

import com.novelverse.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email_tokens")
public class EmailToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserAccount user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EmailTokenType type;

  @Column(
      name = "token_hash",
      nullable = false,
      unique = true,
      length = 64,
      columnDefinition = "char(64)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected EmailToken() {}

  public EmailToken(UserAccount user, EmailTokenType type, String tokenHash, Instant expiresAt) {
    this.user = user;
    this.type = type;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public boolean isUsableAt(Instant now) {
    return usedAt == null && expiresAt.isAfter(now);
  }

  public void markUsed(Instant now) {
    usedAt = now;
  }

  public Long getId() {
    return id;
  }

  public UserAccount getUser() {
    return user;
  }

  public EmailTokenType getType() {
    return type;
  }
}
