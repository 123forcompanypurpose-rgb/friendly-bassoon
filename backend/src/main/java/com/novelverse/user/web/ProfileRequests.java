package com.novelverse.user.web;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;

public final class ProfileRequests {

  private ProfileRequests() {}

  public record UpdateProfileRequest(
      @Size(min = 1, max = 60) String displayName,
      @Size(max = 500) String bio,
      Map<String, String> socialLinks,
      @Past LocalDate birthDate,
      Boolean showMature) {}
}
