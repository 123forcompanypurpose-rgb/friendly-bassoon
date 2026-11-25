package com.novelverse.content.web;

import com.novelverse.content.service.ChapterImageStorageService;
import com.novelverse.content.web.ContentRequests.ConfirmChapterImageRequest;
import com.novelverse.content.web.ContentRequests.PresignChapterImageRequest;
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
@RequestMapping("/api/v1/chapters/{chapterId}/images")
@Tag(name = "Chapter image uploads")
public class ChapterImageController {

  private final ChapterImageStorageService imageStorageService;

  public ChapterImageController(ChapterImageStorageService imageStorageService) {
    this.imageStorageService = imageStorageService;
  }

  @PostMapping("/presign")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a short-lived URL for uploading a chapter image")
  public ChapterImageStorageService.UploadLink presign(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long chapterId,
      @Valid @RequestBody PresignChapterImageRequest request) {
    return imageStorageService.createUploadLink(
        principal.id(), chapterId, request.contentType(), request.contentLength());
  }

  @PostMapping("/confirm")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Validate and publish an uploaded chapter image")
  public ResponseEntity<ChapterImageStorageService.ImageResult> confirm(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long chapterId,
      @Valid @RequestBody ConfirmChapterImageRequest request) {
    return ResponseEntity.ok(
        imageStorageService.confirmUpload(principal.id(), chapterId, request.objectKey()));
  }
}
