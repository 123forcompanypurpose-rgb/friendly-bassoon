package com.novelverse.auth.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select token from RefreshToken token
            join fetch token.user
            where token.tokenHash = :tokenHash
            """)
  Optional<RefreshToken> findForUpdate(@Param("tokenHash") String tokenHash);

  List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

  List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

  Optional<RefreshToken> findByIdAndFamilyId(Long id, UUID familyId);
}
