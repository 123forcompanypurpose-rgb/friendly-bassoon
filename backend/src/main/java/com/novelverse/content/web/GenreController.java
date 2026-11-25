package com.novelverse.content.web;

import com.novelverse.content.service.TagService;
import com.novelverse.content.web.ContentResponses.GenreResponse;
import com.novelverse.content.web.ContentResponses.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
@Tag(name = "Genres and tags")
public class GenreController {

  private final TagService tagService;

  public GenreController(TagService tagService) {
    this.tagService = tagService;
  }

  @GetMapping("/genres")
  @Operation(summary = "List active story genres")
  public List<GenreResponse> genres() {
    return tagService.genres();
  }

  @GetMapping("/tags")
  @Operation(summary = "Search common story tags")
  public List<TagResponse> tags(@RequestParam(required = false) @Size(max = 40) String q) {
    return tagService.tags(q);
  }
}
