package com.novelverse.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class IpHashService {

  private final byte[] salt;

  public IpHashService(@Value("${app.ip-hash-salt}") String salt) {
    if (salt == null || salt.length() < 16) {
      throw new IllegalStateException("IP_HASH_SALT must contain at least 16 characters.");
    }
    this.salt = salt.getBytes(StandardCharsets.UTF_8);
  }

  public String hash(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(salt, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Unable to hash client address.", exception);
    }
  }
}
