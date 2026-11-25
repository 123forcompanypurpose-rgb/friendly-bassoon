package com.novelverse.storage.web;

import com.novelverse.security.UserPrincipal;
import com.novelverse.storage.service.AvatarStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/me/avatar")
@Tag(name = "Avatar uploads")
public class AvatarController {

  public record PresignRequest(
      @NotBlank @Pattern(regexp = "image/(jpeg|png)") String contentType,
      @Min(1) @Max(2_097_152) long contentLength) {}

  public record ConfirmRequest(@NotBlank @Size(max = 200) String objectKey) {}

  private final AvatarStorageService avatarStorageService;

  public AvatarController(AvatarStorageService avatarStorageService) {
    this.avatarStorageService = avatarStorageService;
  }

  @PostMapping("/presign")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a short-lived URL for uploading a profile avatar")
  public AvatarStorageService.UploadLink presign(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody PresignRequest request) {
    return avatarStorageService.createUploadLink(
        principal.id(), request.contentType(), request.contentLength());
  }

  @PostMapping("/confirm")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Validate, normalize, and save an uploaded profile avatar")
  public ResponseEntity<AvatarStorageService.AvatarResult> confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ConfirmRequest request) {
    return ResponseEntity.ok(
        avatarStorageService.confirmUpload(principal.id(), request.objectKey()));
  }
}
