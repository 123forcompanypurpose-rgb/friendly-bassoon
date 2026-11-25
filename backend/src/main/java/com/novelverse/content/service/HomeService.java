package com.novelverse.content.service;

import com.novelverse.content.domain.GenreRepository;
import com.novelverse.content.domain.Story;
import com.novelverse.content.domain.StoryRepository;
import com.novelverse.content.web.ContentResponses.GenreResponse;
import com.novelverse.content.web.ContentResponses.HomeResponse;
import com.novelverse.content.web.ContentResponses.StoryResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HomeService {

  private static final int SECTION_SIZE = 8;

  private final StoryRepository storyRepository;
  private final GenreRepository genreRepository;
  private final ContentAccess contentAccess;

  public HomeService(
      StoryRepository storyRepository,
      GenreRepository genreRepository,
      ContentAccess contentAccess) {
    this.storyRepository = storyRepository;
    this.genreRepository = genreRepository;
    this.contentAccess = contentAccess;
  }

  @Transactional(readOnly = true)
  public HomeResponse getHome(Long userId) {
    boolean includeMature = contentAccess.canReadMature(userId);
    PageRequest firstPage = PageRequest.of(0, SECTION_SIZE);
    return new HomeResponse(
        map(storyRepository.findFeaturedPublicStories(includeMature, firstPage).getContent()),
        map(storyRepository.findMostReadPublicStories(null, includeMature, firstPage).getContent()),
        map(storyRepository.findNewestPublicStories(null, includeMature, firstPage).getContent()),
        map(storyRepository.findPublicStories(null, includeMature, firstPage).getContent()),
        map(storyRepository.findCompletedPublicStories(includeMature, firstPage).getContent()),
        genreRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
            .map(GenreResponse::from)
            .toList());
  }

  private List<StoryResponse> map(List<Story> stories) {
    return stories.stream().map(StoryResponse::from).toList();
  }
}
