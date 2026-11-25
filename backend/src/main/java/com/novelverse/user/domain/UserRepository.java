package com.novelverse.user.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  Optional<UserAccount> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

  Optional<UserAccount> findByGoogleSubAndDeletedAtIsNull(String googleSub);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByRoleAndStatus(UserRole role, UserStatus status);

  boolean existsByRole(UserRole role);
}
