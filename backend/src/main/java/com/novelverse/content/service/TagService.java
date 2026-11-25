package com.novelverse.content.service;

import com.novelverse.content.domain.GenreRepository;
import com.novelverse.content.domain.TagRepository;
import com.novelverse.content.web.ContentResponses.GenreResponse;
import com.novelverse.content.web.ContentResponses.TagResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {

  private final GenreRepository genreRepository;
  private final TagRepository tagRepository;

  public TagService(GenreRepository genreRepository, TagRepository tagRepository) {
    this.genreRepository = genreRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional(readOnly = true)
  public List<GenreResponse> genres() {
    return genreRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
        .map(GenreResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<TagResponse> tags(String query) {
    String normalizedQuery = query == null ? "" : query.strip();
    return tagRepository
        .findTop20ByNameContainingIgnoreCaseOrderByUseCountDescNameAsc(normalizedQuery)
        .stream()
        .map(TagResponse::from)
        .toList();
  }
}
