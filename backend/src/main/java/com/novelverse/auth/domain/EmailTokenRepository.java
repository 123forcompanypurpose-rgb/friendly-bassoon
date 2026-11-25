package com.novelverse.auth.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

  @Query(
      """
            select token from EmailToken token
            join fetch token.user
            where token.tokenHash = :tokenHash and token.type = :type
            """)
  Optional<EmailToken> findForUse(
      @Param("tokenHash") String tokenHash, @Param("type") EmailTokenType type);

  @Modifying
  @Query(
      """
            update EmailToken token set token.usedAt = CURRENT_TIMESTAMP
            where token.user.id = :userId and token.type = :type and token.usedAt is null
            """)
  int consumeOutstanding(@Param("userId") Long userId, @Param("type") EmailTokenType type);

  List<EmailToken> findByUserIdAndTypeAndUsedAtIsNull(Long userId, EmailTokenType type);
}
