package com.novelverse.content.web;

import com.novelverse.content.service.ChapterViewService;
import com.novelverse.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chapters")
@Tag(name = "Reading")
public class ChapterViewController {

  private final ChapterViewService chapterViewService;

  public ChapterViewController(ChapterViewService chapterViewService) {
    this.chapterViewService = chapterViewService;
  }

  @PostMapping("/{chapterId}/view")
  @Operation(summary = "Record a deduplicated chapter view without blocking the reader")
  public ResponseEntity<Void> recordView(
      @PathVariable long chapterId,
      @AuthenticationPrincipal UserPrincipal principal,
      HttpServletRequest request) {
    chapterViewService.record(
        chapterId,
        principal == null ? null : principal.id(),
        request.getRemoteAddr(),
        request.getHeader("User-Agent"));
    return ResponseEntity.noContent().build();
  }
}
