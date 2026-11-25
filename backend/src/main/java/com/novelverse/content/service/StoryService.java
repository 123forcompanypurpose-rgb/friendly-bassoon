package com.novelverse.content.service;

import com.novelverse.common.ApiException;
import com.novelverse.content.domain.Genre;
import com.novelverse.content.domain.GenreRepository;
import com.novelverse.content.domain.Story;
import com.novelverse.content.domain.StoryMaturity;
import com.novelverse.content.domain.StoryRepository;
import com.novelverse.content.domain.StoryStatus;
import com.novelverse.content.domain.StoryVisibility;
import com.novelverse.content.domain.Tag;
import com.novelverse.content.domain.TagRepository;
import com.novelverse.content.web.ContentRequests.CreateStoryRequest;
import com.novelverse.content.web.ContentRequests.UpdateStoryRequest;
import com.novelverse.content.web.ContentResponses.PageResponse;
import com.novelverse.content.web.ContentResponses.StoryResponse;
import com.novelverse.user.domain.UserAccount;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StoryService {

  private final StoryRepository storyRepository;
  private final GenreRepository genreRepository;
  private final TagRepository tagRepository;
  private final ContentAccess contentAccess;
  private final HtmlContentSanitizer sanitizer;
  private final JdbcTemplate jdbcTemplate;
  private final EntityManager entityManager;

  public StoryService(
      StoryRepository storyRepository,
      GenreRepository genreRepository,
      TagRepository tagRepository,
      ContentAccess contentAccess,
      HtmlContentSanitizer sanitizer,
      JdbcTemplate jdbcTemplate,
      EntityManager entityManager) {
    this.storyRepository = storyRepository;
    this.genreRepository = genreRepository;
    this.tagRepository = tagRepository;
    this.contentAccess = contentAccess;
    this.sanitizer = sanitizer;
    this.jdbcTemplate = jdbcTemplate;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public PageResponse<StoryResponse> browse(
      String genreSlug, Long userId, int page, int size, String sort) {
    boolean includeMature = contentAccess.canReadMature(userId);
    String normalizedGenre =
        StringUtils.hasText(genreSlug) ? genreSlug.trim().toLowerCase(Locale.ROOT) : null;
    PageRequest pageable = PageRequest.of(page, size);
    Page<Story> stories =
        switch (sort == null ? "updated" : sort.trim().toLowerCase(Locale.ROOT)) {
          case "updated" ->
              storyRepository.findPublicStories(normalizedGenre, includeMature, pageable);
          case "newest" ->
              storyRepository.findNewestPublicStories(normalizedGenre, includeMature, pageable);
          case "popular" ->
              storyRepository.findMostReadPublicStories(normalizedGenre, includeMature, pageable);
          case "top-rated" ->
              storyRepository.findTopRatedPublicStories(normalizedGenre, includeMature, pageable);
          default ->
              throw new ApiException(
                  HttpStatus.BAD_REQUEST,
                  "INVALID_STORY_SORT",
                  "Choose updated, newest, popular, or top-rated sorting.");
        };
    return mapPage(stories);
  }

  @Transactional(readOnly = true)
  public PageResponse<StoryResponse> listMine(Long userId, int page, int size) {
    contentAccess.requireVerifiedAuthor(userId);
    return mapPage(
        storyRepository.findByAuthor_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            userId, PageRequest.of(page, size)));
  }

  @Transactional(readOnly = true)
  public StoryResponse get(Long storyId, Long userId) {
    boolean moderator = contentAccess.canModerate(userId);
    Story story =
        moderator
            ? storyRepository.findById(storyId).orElseThrow(contentAccess::notFound)
            : findStory(storyId);
    boolean owner = userId != null && story.getAuthor().getId().equals(userId);
    if (story.getDeletedAt() != null && !moderator) {
      throw contentAccess.notFound();
    }
    if (!owner
        && !moderator
        && story.getVisibility() != StoryVisibility.PUBLISHED
        && story.getVisibility() != StoryVisibility.UNLISTED) {
      throw contentAccess.notFound();
    }
    if (!owner
        && !moderator
        && story.getMaturity() == StoryMaturity.MATURE
        && !contentAccess.canReadMature(userId)) {
      throw contentAccess.notFound();
    }
    return StoryResponse.from(story);
  }

  @Transactional
  public StoryResponse create(Long userId, CreateStoryRequest request) {
    UserAccount author = contentAccess.requireVerifiedAuthor(userId);
    Genre genre = findGenre(request.genreId());
    String title = request.title().strip();
    if (title.length() < 3) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_TITLE",
          "Story titles must contain at least 3 characters.");
    }
    String slug = uniqueSlug(title);
    String blurb = sanitizeBlurb(request.blurb());
    Story story =
        new Story(
            author,
            title,
            slug,
            blurb,
            genre,
            defaultLanguage(request.language()),
            request.maturity() == null ? StoryMaturity.EVERYONE : request.maturity(),
            request.status() == null ? StoryStatus.ONGOING : request.status());
    story.setTags(resolveTags(request.tags()));
    Story saved = storyRepository.saveAndFlush(story);
    updateTagCounts(saved.getTags(), Set.of());
    return StoryResponse.from(saved);
  }

  @Transactional
  public StoryResponse update(Long userId, Long storyId, UpdateStoryRequest request) {
    contentAccess.requireVerifiedAuthor(userId);
    Story story = requireOwnedForUpdate(userId, storyId);
    String title = request.title() == null ? story.getTitle() : request.title().strip();
    if (title.length() < 3) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_TITLE",
          "Story titles must contain at least 3 characters.");
    }
    Genre genre = request.genreId() == null ? story.getGenre() : findGenre(request.genreId());
    String language = request.language() == null ? story.getLanguage() : request.language().strip();
    StoryMaturity maturity = request.maturity() == null ? story.getMaturity() : request.maturity();
    StoryStatus status = request.status() == null ? story.getStatus() : request.status();
    String blurb = request.blurb() == null ? story.getBlurb() : sanitizeBlurb(request.blurb());
    Set<Tag> oldTags = new LinkedHashSet<>(story.getTags());
    Set<Tag> newTags = request.tags() == null ? oldTags : resolveTags(request.tags());

    story.update(
        title,
        title.equals(story.getTitle()) ? story.getSlug() : uniqueSlug(title),
        blurb,
        genre,
        language,
        maturity,
        status);
    story.setTags(newTags);
    updateTagCounts(newTags, oldTags);
    return StoryResponse.from(storyRepository.saveAndFlush(story));
  }

  @Transactional
  public void delete(Long userId, Long storyId) {
    contentAccess.requireVerifiedAuthor(userId);
    Story story = requireOwnedForUpdate(userId, storyId);
    updateTagCounts(Set.of(), story.getTags());
    story.markDeleted(Instant.now());
  }

  @Transactional
  public StoryResponse publish(Long userId, Long storyId, boolean unlisted) {
    contentAccess.requireVerifiedAuthor(userId);
    Story story = requireOwnedForUpdate(userId, storyId);
    long publishedChapters =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM chapters
            WHERE story_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL
            """,
            Long.class,
            storyId);
    if (publishedChapters == 0) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "STORY_REQUIRES_PUBLISHED_CHAPTER",
          "Publish at least one chapter before publishing the story.");
    }
    story.publish(Instant.now());
    if (unlisted) {
      story.setUnlisted();
    }
    return StoryResponse.from(storyRepository.saveAndFlush(story));
  }

  @Transactional
  public StoryResponse unpublish(Long userId, Long storyId) {
    contentAccess.requireVerifiedAuthor(userId);
    Story story = requireOwnedForUpdate(userId, storyId);
    story.unpublish();
    return StoryResponse.from(storyRepository.saveAndFlush(story));
  }

  @Transactional
  public void requireOwnedStory(Long userId, Long storyId) {
    contentAccess.requireVerifiedAuthor(userId);
    requireOwnedForUpdate(userId, storyId);
  }

  @Transactional
  public void updateCover(Long userId, Long storyId, String url) {
    contentAccess.requireVerifiedAuthor(userId);
    Story story = requireOwnedForUpdate(userId, storyId);
    story.setCoverUrl(url);
  }

  private PageResponse<StoryResponse> mapPage(Page<Story> page) {
    return new PageResponse<>(
        page.getContent().stream().map(StoryResponse::from).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }

  private Story findStory(Long storyId) {
    return storyRepository.findByIdAndDeletedAtIsNull(storyId).orElseThrow(contentAccess::notFound);
  }

  private Story requireOwnedForUpdate(Long userId, Long storyId) {
    Story story = storyRepository.findByIdForUpdate(storyId).orElseThrow(contentAccess::notFound);
    if (!story.getAuthor().getId().equals(userId)) {
      throw contentAccess.notFound();
    }
    return story;
  }

  private Genre findGenre(Short genreId) {
    return genreRepository
        .findById(genreId)
        .filter(Genre::isActive)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_GENRE", "Choose an active story genre."));
  }

  private String sanitizeBlurb(String blurb) {
    String safe = sanitizer.sanitize(blurb).html();
    if (safe.length() > 2000) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "BLURB_TOO_LONG",
          "The story blurb cannot exceed 2,000 characters.");
    }
    return safe;
  }

  private String defaultLanguage(String language) {
    return StringUtils.hasText(language) ? language.strip().replace('_', '-') : "en";
  }

  private String uniqueSlug(String title) {
    String base = slugify(title);
    String candidate = base;
    int suffix = 2;
    while (storyRepository.existsBySlug(candidate)) {
      String tail = "-" + suffix++;
      candidate = base.substring(0, Math.min(base.length(), 140 - tail.length())) + tail;
    }
    return candidate;
  }

  private String slugify(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD);
    String ascii = normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    String slug = ascii.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return slug.isEmpty() ? "story" : slug.substring(0, Math.min(140, slug.length()));
  }

  private Set<Tag> resolveTags(List<String> requestedTags) {
    if (requestedTags == null || requestedTags.isEmpty()) {
      return Set.of();
    }
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (String tag : requestedTags) {
      String normalized = tag.strip().toLowerCase(Locale.ROOT);
      if (!normalized.isEmpty()) {
        names.add(normalized);
      }
    }
    if (names.size() > 10) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "TOO_MANY_TAGS", "A story can have no more than 10 tags.");
    }
    Set<Tag> tags = new LinkedHashSet<>();
    for (String name : names) {
      tags.add(resolveTag(name));
    }
    return tags;
  }

  private Tag resolveTag(String name) {
    if (name.length() > 40) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_TAG", "Tag names cannot exceed 40 characters.");
    }
    var existing = tagRepository.findByNameIgnoreCase(name);
    if (existing.isPresent()) {
      return existing.get();
    }

    String baseSlug = slugify(name);
    String slug = baseSlug.substring(0, Math.min(43, baseSlug.length()));
    if (tagRepository.findBySlug(slug).isPresent()) {
      String suffix = "-" + hashSuffix(name);
      slug = baseSlug.substring(0, Math.min(50 - suffix.length(), baseSlug.length())) + suffix;
    }
    Long tagId;
    try {
      tagId =
          jdbcTemplate.queryForObject(
              """
              INSERT INTO tags (name, slug) VALUES (?, ?)
              ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
              RETURNING id
              """,
              Long.class,
              name,
              slug);
    } catch (EmptyResultDataAccessException exception) {
      throw new IllegalStateException("Tag insert did not return an ID.", exception);
    }
    return entityManager.find(Tag.class, tagId);
  }

  private String hashSuffix(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return IntStream.range(0, 4)
          .mapToObj(index -> String.format("%02x", digest[index]))
          .reduce("", String::concat);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the runtime.", exception);
    }
  }

  private void updateTagCounts(Set<Tag> currentTags, Set<Tag> previousTags) {
    Set<Long> currentIds =
        currentTags.stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
    Set<Long> previousIds =
        previousTags.stream().map(Tag::getId).collect(java.util.stream.Collectors.toSet());
    List<Long> added = currentIds.stream().filter(id -> !previousIds.contains(id)).toList();
    List<Long> removed = previousIds.stream().filter(id -> !currentIds.contains(id)).toList();
    added.forEach(
        id -> jdbcTemplate.update("UPDATE tags SET use_count = use_count + 1 WHERE id = ?", id));
    removed.forEach(
        id ->
            jdbcTemplate.update(
                "UPDATE tags SET use_count = GREATEST(use_count - 1, 0) WHERE id = ?", id));
  }
}
