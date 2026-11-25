package com.novelverse.content.web;

import com.novelverse.content.service.StoryCoverStorageService;
import com.novelverse.content.web.ContentRequests.ConfirmCoverRequest;
import com.novelverse.content.web.ContentRequests.PresignCoverRequest;
import com.novelverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stories/{storyId}/cover")
@Tag(name = "Story cover uploads")
public class StoryCoverController {

  private final StoryCoverStorageService coverStorageService;

  public StoryCoverController(StoryCoverStorageService coverStorageService) {
    this.coverStorageService = coverStorageService;
  }

  @PostMapping("/presign")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a short-lived URL for uploading a story cover")
  public StoryCoverStorageService.UploadLink presign(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long storyId,
      @Valid @RequestBody PresignCoverRequest request) {
    return coverStorageService.createUploadLink(
        principal.id(), storyId, request.contentType(), request.contentLength());
  }

  @PostMapping("/confirm")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Validate, resize, convert, and save a story cover as WebP")
  public ResponseEntity<StoryCoverStorageService.CoverResult> confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long storyId,
      @Valid @RequestBody ConfirmCoverRequest request) {
    return ResponseEntity.ok(
        coverStorageService.confirmUpload(principal.id(), storyId, request.objectKey()));
  }
}
