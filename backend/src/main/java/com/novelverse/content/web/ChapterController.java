package com.novelverse.content.web;

import com.novelverse.content.service.ChapterService;
import com.novelverse.content.web.ContentRequests.CreateChapterRequest;
import com.novelverse.content.web.ContentRequests.PublishChapterRequest;
import com.novelverse.content.web.ContentRequests.ReorderChaptersRequest;
import com.novelverse.content.web.ContentRequests.SaveChapterDraftRequest;
import com.novelverse.content.web.ContentRequests.ScheduleChapterRequest;
import com.novelverse.content.web.ContentResponses.ChapterResponse;
import com.novelverse.content.web.ContentResponses.ChapterSummary;
import com.novelverse.content.web.ContentResponses.PageResponse;
import com.novelverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Chapters")
public class ChapterController {

  private final ChapterService chapterService;

  public ChapterController(ChapterService chapterService) {
    this.chapterService = chapterService;
  }

  @GetMapping("/stories/{storyId}/chapters")
  @Operation(summary = "List published chapters, or all chapters for the author")
  public PageResponse<ChapterSummary> list(
      @PathVariable long storyId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(50) int size,
      @AuthenticationPrincipal UserPrincipal principal) {
    return chapterService.list(storyId, principal == null ? null : principal.id(), page, size);
  }

  @PostMapping("/stories/{storyId}/chapters")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a chapter and its initial draft")
  public ResponseEntity<ChapterResponse> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long storyId,
      @Valid @RequestBody CreateChapterRequest request) {
    ChapterResponse response = chapterService.create(principal.id(), storyId, request);
    return ResponseEntity.created(
            ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/v1/chapters/{id}")
                .buildAndExpand(response.id())
                .toUri())
        .body(response);
  }

  @GetMapping("/chapters/{chapterId}")
  @Operation(summary = "Read a published chapter or your own draft")
  public ChapterResponse get(
      @PathVariable long chapterId, @AuthenticationPrincipal UserPrincipal principal) {
    return chapterService.get(chapterId, principal == null ? null : principal.id());
  }

  @GetMapping("/chapters/{chapterId}/draft")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Read the working draft for a chapter")
  public ChapterResponse getDraft(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long chapterId) {
    return chapterService.getDraft(principal.id(), chapterId);
  }

  @PutMapping("/chapters/{chapterId}/draft")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Autosave a chapter draft with optimistic version checking")
  public ChapterResponse saveDraft(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long chapterId,
      @Valid @RequestBody SaveChapterDraftRequest request) {
    return chapterService.saveDraft(principal.id(), chapterId, request);
  }

  @PostMapping("/chapters/{chapterId}/publish")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Publish the latest saved chapter draft")
  public ChapterResponse publish(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long chapterId,
      @Valid @RequestBody PublishChapterRequest request) {
    return chapterService.publish(principal.id(), chapterId, request);
  }

  @PostMapping("/chapters/{chapterId}/schedule")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Schedule a draft chapter for future publication")
  public ChapterResponse schedule(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long chapterId,
      @Valid @RequestBody ScheduleChapterRequest request) {
    return chapterService.schedule(principal.id(), chapterId, request);
  }

  @PostMapping("/chapters/{chapterId}/unpublish")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Unpublish a chapter")
  public ChapterResponse unpublish(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long chapterId) {
    return chapterService.unpublish(principal.id(), chapterId);
  }

  @DeleteMapping("/chapters/{chapterId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Delete a chapter")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long chapterId) {
    chapterService.delete(principal.id(), chapterId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/stories/{storyId}/chapters/order")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Reorder all active chapters in a story")
  public ResponseEntity<Void> reorder(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long storyId,
      @Valid @RequestBody ReorderChaptersRequest request) {
    chapterService.reorder(principal.id(), storyId, request);
    return ResponseEntity.noContent().build();
  }
}
