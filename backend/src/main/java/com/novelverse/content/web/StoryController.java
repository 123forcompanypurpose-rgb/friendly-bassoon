package com.novelverse.content.web;

import com.novelverse.content.service.StoryService;
import com.novelverse.content.web.ContentRequests.CreateStoryRequest;
import com.novelverse.content.web.ContentRequests.UpdateStoryRequest;
import com.novelverse.content.web.ContentResponses.PageResponse;
import com.novelverse.content.web.ContentResponses.StoryResponse;
import com.novelverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
@Tag(name = "Stories")
public class StoryController {

  private final StoryService storyService;

  public StoryController(StoryService storyService) {
    this.storyService = storyService;
  }

  @GetMapping("/stories")
  @Operation(summary = "Browse published stories")
  public PageResponse<StoryResponse> browse(
      @RequestParam(required = false) @Size(max = 60) String genre,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
      @RequestParam(defaultValue = "updated") @Pattern(regexp = "updated|newest|popular|top-rated")
          String sort,
      @AuthenticationPrincipal UserPrincipal principal) {
    return storyService.browse(genre, principal == null ? null : principal.id(), page, size, sort);
  }

  @GetMapping("/stories/{storyId}")
  @Operation(summary = "Get a published story or one of your own drafts")
  public StoryResponse get(
      @PathVariable long storyId, @AuthenticationPrincipal UserPrincipal principal) {
    return storyService.get(storyId, principal == null ? null : principal.id());
  }

  @GetMapping("/me/stories")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "List your stories, including drafts")
  public PageResponse<StoryResponse> mine(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
    return storyService.listMine(principal.id(), page, size);
  }

  @PostMapping("/stories")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Create a story draft")
  public ResponseEntity<StoryResponse> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody CreateStoryRequest request) {
    StoryResponse response = storyService.create(principal.id(), request);
    return ResponseEntity.created(
            ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri())
        .body(response);
  }

  @PutMapping("/stories/{storyId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Update your story metadata")
  public StoryResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable long storyId,
      @Valid @RequestBody UpdateStoryRequest request) {
    return storyService.update(principal.id(), storyId, request);
  }

  @DeleteMapping("/stories/{storyId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Delete your story")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long storyId) {
    storyService.delete(principal.id(), storyId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/stories/{storyId}/publish")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Publish your story")
  public StoryResponse publish(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long storyId) {
    return storyService.publish(principal.id(), storyId, false);
  }

  @PostMapping("/stories/{storyId}/unlist")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Hide your story from browse listings while keeping its direct link")
  public StoryResponse unlist(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long storyId) {
    return storyService.publish(principal.id(), storyId, true);
  }

  @PostMapping("/stories/{storyId}/unpublish")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Return your published story to draft visibility")
  public StoryResponse unpublish(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable long storyId) {
    return storyService.unpublish(principal.id(), storyId);
  }
}
