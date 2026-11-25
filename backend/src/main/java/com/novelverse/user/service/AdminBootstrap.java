package com.novelverse.user.service;

import com.novelverse.auth.service.PasswordPolicy;
import com.novelverse.user.domain.UserAccount;
import com.novelverse.user.domain.UserRepository;
import com.novelverse.user.domain.UserRole;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final String adminEmail;
  private final String adminPassword;

  public AdminBootstrap(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy,
      @Value("${ADMIN_EMAIL:}") String adminEmail,
      @Value("${ADMIN_PASSWORD:}") String adminPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
    this.adminPassword = adminPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (adminEmail.isBlank() && adminPassword.isBlank()) {
      return;
    }
    if (adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
      throw new IllegalStateException(
          "Set both ADMIN_EMAIL and ADMIN_PASSWORD to bootstrap an administrator.");
    }
    if (userRepository.existsByRole(UserRole.ADMIN)) {
      return;
    }
    passwordPolicy.validate(adminPassword);
    UserAccount user =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull(adminEmail)
            .orElseGet(
                () ->
                    new UserAccount(
                        adminEmail,
                        uniqueAdminUsername(),
                        "NovelVerse Admin",
                        passwordEncoder.encode(adminPassword)));
    user.setRole(UserRole.ADMIN);
    user.setPasswordHash(passwordEncoder.encode(adminPassword));
    user.verifyEmail();
    user.acceptTerms(Instant.now());
    userRepository.save(user);
  }

  private String uniqueAdminUsername() {
    String username = "novelverse_admin";
    for (int suffix = 1; userRepository.existsByUsernameIgnoreCase(username); suffix++) {
      username = "novelverse_admin_" + suffix;
    }
    return username.substring(0, Math.min(20, username.length()));
  }
}
