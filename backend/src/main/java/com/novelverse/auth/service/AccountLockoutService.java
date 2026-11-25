package com.novelverse.auth.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AccountLockoutService {

  private static final String FIND_LOCK_SQL =
      "select locked_until from login_attempts where identity_hash = ? and ip_hash = ? and locked_until is not null";
  private static final String RECORD_FAILURE_SQL =
      """
            insert into login_attempts(identity_hash, ip_hash, failed_attempts, locked_until, updated_at)
            values (?, ?, 1, null, now())
            on conflict (identity_hash, ip_hash) do update set
                failed_attempts = case
                    when login_attempts.locked_until > now() then login_attempts.failed_attempts
                    else login_attempts.failed_attempts + 1
                end,
                locked_until = case
                    when login_attempts.locked_until > now() then login_attempts.locked_until
                    when login_attempts.failed_attempts + 1 >= 5 then now() + interval '15 minutes'
                    else null
                end,
                updated_at = now()
            returning locked_until
            """;

  private final JdbcTemplate jdbcTemplate;

  public AccountLockoutService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean isLocked(String identityHash, String ipHash, Instant now) {
    return findLock(identityHash, ipHash).filter(until -> until.isAfter(now)).isPresent();
  }

  public boolean recordFailure(String identityHash, String ipHash, Instant now) {
    Timestamp lockedUntil =
        jdbcTemplate.queryForObject(
            RECORD_FAILURE_SQL,
            (result, row) -> result.getTimestamp("locked_until"),
            identityHash,
            ipHash);
    return lockedUntil != null && lockedUntil.toInstant().isAfter(now);
  }

  public void clear(String identityHash, String ipHash) {
    jdbcTemplate.update(
        "delete from login_attempts where identity_hash = ? and ip_hash = ?", identityHash, ipHash);
  }

  private Optional<Instant> findLock(String identityHash, String ipHash) {
    return jdbcTemplate
        .query(
            FIND_LOCK_SQL,
            (result, row) -> {
              Timestamp lockedUntil = result.getTimestamp("locked_until");
              return lockedUntil.toInstant();
            },
            identityHash,
            ipHash)
        .stream()
        .findFirst();
  }
}
