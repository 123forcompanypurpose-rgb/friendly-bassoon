package com.novelverse.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

  private static final Set<String> COMMON_PASSWORDS =
      Set.of(
          "12345678",
          "password",
          "password1",
          "qwerty123",
          "letmein123",
          "welcome1",
          "admin123",
          "iloveyou",
          "changeme",
          "1234567890");

  public void validate(String password) {
    String normalized = password.toLowerCase(Locale.ROOT);
    if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes.");
    }
    if (COMMON_PASSWORDS.contains(normalized)
        || normalized.matches("(.)\\1{7,}")
        || normalized.matches("(0123|1234|2345|3456|4567|5678|6789).{0,4}")) {
      throw new IllegalArgumentException("Choose a less common password.");
    }
  }
}
