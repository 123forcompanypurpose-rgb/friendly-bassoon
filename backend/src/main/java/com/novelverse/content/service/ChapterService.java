package com.novelverse.content.service;

import com.novelverse.common.ApiException;
import com.novelverse.content.domain.Chapter;
import com.novelverse.content.domain.ChapterDraft;
import com.novelverse.content.domain.ChapterDraftRepository;
import com.novelverse.content.domain.ChapterRepository;
import com.novelverse.content.domain.ChapterStatus;
import com.novelverse.content.domain.Story;
import com.novelverse.content.domain.StoryRepository;
import com.novelverse.content.domain.StoryVisibility;
import com.novelverse.content.web.ContentRequests.CreateChapterRequest;
import com.novelverse.content.web.ContentRequests.PublishChapterRequest;
import com.novelverse.content.web.ContentRequests.ReorderChaptersRequest;
import com.novelverse.content.web.ContentRequests.SaveChapterDraftRequest;
import com.novelverse.content.web.ContentRequests.ScheduleChapterRequest;
import com.novelverse.content.web.ContentResponses.ChapterResponse;
import com.novelverse.content.web.ContentResponses.ChapterSummary;
import com.novelverse.content.web.ContentResponses.PageResponse;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterService {

  private final ChapterRepository chapterRepository;
  private final ChapterDraftRepository chapterDraftRepository;
  private final StoryRepository storyRepository;
  private final ContentAccess contentAccess;
  private final HtmlContentSanitizer sanitizer;
  private final JdbcTemplate jdbcTemplate;

  public ChapterService(
      ChapterRepository chapterRepository,
      ChapterDraftRepository chapterDraftRepository,
      StoryRepository storyRepository,
      ContentAccess contentAccess,
      HtmlContentSanitizer sanitizer,
      JdbcTemplate jdbcTemplate) {
    this.chapterRepository = chapterRepository;
    this.chapterDraftRepository = chapterDraftRepository;
    this.storyRepository = storyRepository;
    this.contentAccess = contentAccess;
    this.sanitizer = sanitizer;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public ChapterResponse create(Long userId, Long storyId, CreateChapterRequest request) {
    Story story = requireOwnedStoryForUpdate(userId, storyId);
    int number = chapterRepository.findMaxActiveNumber(storyId) + 1;
    String title = request.title().strip();
    HtmlContentSanitizer.SanitizedContent content = sanitizer.sanitize(request.contentHtml());
    String authorNote = cleanNote(request.authorNote());
    Chapter chapter =
        chapterRepository.saveAndFlush(
            new Chapter(story, number, title, content.html(), authorNote, content.wordCount()));
    chapterDraftRepository.saveAndFlush(
        new ChapterDraft(chapter, title, content.html(), authorNote, chapter.getVersion()));
    return ChapterResponse.from(chapter);
  }

  @Transactional(readOnly = true)
  public PageResponse<ChapterSummary> list(Long storyId, Long userId, int page, int size) {
    Story story =
        storyRepository.findByIdAndDeletedAtIsNull(storyId).orElseThrow(contentAccess::notFound);
    boolean owner = userId != null && story.getAuthor().getId().equals(userId);
    if (!owner) {
      if (story.getVisibility() != StoryVisibility.PUBLISHED
          && story.getVisibility() != StoryVisibility.UNLISTED) {
        throw contentAccess.notFound();
      }
      if (!contentAccess.canReadStory(story, userId)) {
        throw contentAccess.notFound();
      }
      Page<Chapter> chapters =
          chapterRepository.findByStory_IdAndStatusAndDeletedAtIsNullOrderByNumberAsc(
              storyId, ChapterStatus.PUBLISHED, PageRequest.of(page, size));
      return mapPage(chapters);
    }

    contentAccess.requireVerifiedAuthor(userId);
    Page<Chapter> chapters =
        chapterRepository.findByStory_IdAndDeletedAtIsNullOrderByNumberAsc(
            storyId, PageRequest.of(page, size));
    var chapterIds = chapters.getContent().stream().map(Chapter::getId).toList();
    var drafts =
        chapterIds.isEmpty()
            ? java.util.Map.<Long, ChapterDraft>of()
            : chapterDraftRepository.findByChapterIdIn(chapterIds).stream()
                .collect(
                    java.util.stream.Collectors.toMap(ChapterDraft::getChapterId, draft -> draft));
    List<ChapterSummary> content =
        chapters.getContent().stream()
            .map(
                chapter ->
                    ChapterSummary.from(
                        chapter,
                        drafts.containsKey(chapter.getId())
                            ? drafts.get(chapter.getId()).getTitle()
                            : chapter.getTitle()))
            .toList();
    return new PageResponse<>(
        content,
        chapters.getNumber(),
        chapters.getSize(),
        chapters.getTotalElements(),
        chapters.getTotalPages());
  }

  @Transactional(readOnly = true)
  public ChapterResponse get(Long chapterId, Long userId) {
    boolean moderator = contentAccess.canModerate(userId);
    Chapter chapter =
        moderator
            ? chapterRepository.findById(chapterId).orElseThrow(contentAccess::notFound)
            : chapterRepository
                .findByIdAndDeletedAtIsNull(chapterId)
                .orElseThrow(contentAccess::notFound);
    if (chapter.getDeletedAt() != null && !moderator) {
      throw contentAccess.notFound();
    }
    Story story = chapter.getStory();
    boolean owner = userId != null && story.getAuthor().getId().equals(userId);
    if (!owner && !moderator) {
      requirePublicStory(story, userId);
      if (chapter.getStatus() != ChapterStatus.PUBLISHED) {
        throw contentAccess.notFound();
      }
      return responseWithNavigation(chapter);
    }
    if (owner) {
      contentAccess.requireVerifiedAuthor(userId);
    }
    if (chapter.getStatus() == ChapterStatus.PUBLISHED) {
      return responseWithNavigation(chapter);
    }
    ChapterDraft draft =
        chapterDraftRepository.findByChapterId(chapterId).orElseThrow(contentAccess::notFound);
    return ChapterResponse.fromDraft(
        chapter, draft, sanitizer.sanitize(draft.getContentHtml()).wordCount());
  }

  @Transactional(readOnly = true)
  public ChapterResponse getDraft(Long userId, Long chapterId) {
    Chapter chapter = requireOwnedChapter(userId, chapterId);
    ChapterDraft draft =
        chapterDraftRepository.findByChapterId(chapterId).orElseThrow(contentAccess::notFound);
    return ChapterResponse.fromDraft(
        chapter, draft, sanitizer.sanitize(draft.getContentHtml()).wordCount());
  }

  @Transactional
  public ChapterResponse saveDraft(Long userId, Long chapterId, SaveChapterDraftRequest request) {
    Chapter chapter = requireOwnedChapterForUpdate(userId, chapterId);
    verifyVersion(chapter, request.expectedVersion());
    HtmlContentSanitizer.SanitizedContent content = sanitizer.sanitize(request.contentHtml());
    String title = request.title().strip();
    String authorNote = cleanNote(request.authorNote());
    ChapterDraft draft =
        chapterDraftRepository.findByChapterId(chapterId).orElseThrow(contentAccess::notFound);
    draft.update(title, content.html(), authorNote, chapter.getVersion() + 1);
    chapter.touchDraft();
    chapterRepository.saveAndFlush(chapter);
    draft.update(title, content.html(), authorNote, chapter.getVersion());
    chapterDraftRepository.saveAndFlush(draft);
    return ChapterResponse.fromDraft(chapter, draft, content.wordCount());
  }

  @Transactional
  public ChapterResponse publish(Long userId, Long chapterId, PublishChapterRequest request) {
    Chapter chapter = requireOwnedChapterForUpdate(userId, chapterId);
    verifyVersion(chapter, request.expectedVersion());
    applyDraftAndPublish(chapter, Instant.now());
    return ChapterResponse.from(chapterRepository.saveAndFlush(chapter));
  }

  @Transactional
  public ChapterResponse schedule(Long userId, Long chapterId, ScheduleChapterRequest request) {
    Chapter chapter = requireOwnedChapterForUpdate(userId, chapterId);
    verifyVersion(chapter, request.expectedVersion());
    if (chapter.getStatus() != ChapterStatus.DRAFT) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CHAPTER_NOT_SCHEDULABLE",
          "Only an unpublished draft chapter can be scheduled.");
    }
    if (!request.publishAt().isAfter(Instant.now())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_PUBLISH_TIME",
          "Scheduled publication time must be in the future.");
    }
    chapter.schedule(request.publishAt());
    chapterRepository.saveAndFlush(chapter);
    syncDraftVersion(chapter);
    ChapterDraft draft =
        chapterDraftRepository.findByChapterId(chapterId).orElseThrow(contentAccess::notFound);
    return ChapterResponse.fromDraft(
        chapter, draft, sanitizer.sanitize(draft.getContentHtml()).wordCount());
  }

  @Transactional
  public ChapterResponse unpublish(Long userId, Long chapterId) {
    Chapter chapter = requireOwnedChapterForUpdate(userId, chapterId);
    if (chapter.getStatus() != ChapterStatus.PUBLISHED) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CHAPTER_NOT_PUBLISHED",
          "Only a published chapter can be unpublished.");
    }
    chapter.unpublish();
    chapterRepository.saveAndFlush(chapter);
    syncDraftVersion(chapter);
    refreshStoryCounters(chapter.getStory());
    ChapterDraft draft =
        chapterDraftRepository.findByChapterId(chapterId).orElseThrow(contentAccess::notFound);
    return ChapterResponse.fromDraft(
        chapter, draft, sanitizer.sanitize(draft.getContentHtml()).wordCount());
  }

  @Transactional
  public void delete(Long userId, Long chapterId) {
    Chapter chapter = requireOwnedChapterForUpdate(userId, chapterId);
    boolean wasPublished = chapter.getStatus() == ChapterStatus.PUBLISHED;
    chapter.markDeleted(Instant.now());
    chapterRepository.saveAndFlush(chapter);
    if (wasPublished) {
      refreshStoryCounters(chapter.getStory());
    }
  }

  @Transactional
  public void reorder(Long userId, Long storyId, ReorderChaptersRequest request) {
    requireOwnedStoryForUpdate(userId, storyId);
    List<Chapter> chapters =
        chapterRepository.findByStory_IdAndDeletedAtIsNullOrderByNumberAsc(storyId);
    List<Long> chapterIds = request.chapterIds();
    Set<Long> uniqueIds = new HashSet<>(chapterIds);
    Set<Long> existingIds =
        chapters.stream().map(Chapter::getId).collect(java.util.stream.Collectors.toSet());
    if (uniqueIds.size() != chapterIds.size() || !uniqueIds.equals(existingIds)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_CHAPTER_ORDER",
          "The order must contain each active chapter exactly once.");
    }
    jdbcTemplate.execute("SET CONSTRAINTS uq_chapters_story_number DEFERRED");
    var byId = chapters.stream().collect(java.util.stream.Collectors.toMap(Chapter::getId, c -> c));
    for (int index = 0; index < chapterIds.size(); index++) {
      byId.get(chapterIds.get(index)).updateNumber(index + 1);
    }
    chapterRepository.flush();
  }

  @Transactional
  public void publishScheduled(Long chapterId, Instant now) {
    Chapter reference =
        chapterRepository
            .findByIdAndDeletedAtIsNull(chapterId)
            .orElseThrow(contentAccess::notFound);
    Story story =
        storyRepository
            .findByIdForUpdate(reference.getStory().getId())
            .orElseThrow(contentAccess::notFound);
    Chapter chapter =
        chapterRepository.findByIdForUpdate(chapterId).orElseThrow(contentAccess::notFound);
    if (chapter.getStatus() != ChapterStatus.SCHEDULED
        || chapter.getPublishAt() == null
        || chapter.getPublishAt().isAfter(now)
        || story.getDeletedAt() != null) {
      return;
    }
    applyDraftAndPublish(chapter, now);
    chapterRepository.saveAndFlush(chapter);
  }

  private void applyDraftAndPublish(Chapter chapter, Instant now) {
    ChapterDraft draft =
        chapterDraftRepository
            .findByChapterId(chapter.getId())
            .orElseThrow(contentAccess::notFound);
    HtmlContentSanitizer.SanitizedContent content = sanitizer.sanitize(draft.getContentHtml());
    chapter.saveDraft(draft.getTitle(), content.html(), draft.getAuthorNote(), content.wordCount());
    chapter.publish(now);
    chapterRepository.saveAndFlush(chapter);
    draft.update(
        chapter.getTitle(),
        chapter.getContentHtml(),
        chapter.getAuthorNote(),
        chapter.getVersion());
    chapterDraftRepository.saveAndFlush(draft);
    refreshStoryCounters(chapter.getStory());
  }

  private void syncDraftVersion(Chapter chapter) {
    chapterDraftRepository
        .findByChapterId(chapter.getId())
        .ifPresent(
            draft -> {
              draft.update(
                  draft.getTitle(),
                  draft.getContentHtml(),
                  draft.getAuthorNote(),
                  chapter.getVersion());
              chapterDraftRepository.saveAndFlush(draft);
            });
  }

  private void refreshStoryCounters(Story story) {
    PublishedCounts counts =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) AS chapters,
                   COALESCE(SUM(word_count), 0) AS words,
                   MAX(published_at) AS latest
            FROM chapters
            WHERE story_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL
            """,
            (resultSet, rowNumber) ->
                new PublishedCounts(
                    Math.toIntExact(resultSet.getLong("chapters")),
                    Math.toIntExact(resultSet.getLong("words")),
                    resultSet.getTimestamp("latest") == null
                        ? null
                        : resultSet.getTimestamp("latest").toInstant()),
            story.getId());
    story.updatePublishedCounts(counts.chapterCount(), counts.wordCount(), counts.lastChapterAt());
    storyRepository.saveAndFlush(story);
  }

  private record PublishedCounts(int chapterCount, int wordCount, Instant lastChapterAt) {}

  private void requirePublicStory(Story story, Long userId) {
    if ((story.getVisibility() != StoryVisibility.PUBLISHED
            && story.getVisibility() != StoryVisibility.UNLISTED)
        || !contentAccess.canReadStory(story, userId)) {
      throw contentAccess.notFound();
    }
  }

  private ChapterResponse responseWithNavigation(Chapter chapter) {
    long storyId = chapter.getStory().getId();
    Long previous =
        chapterRepository
            .findFirstByStory_IdAndStatusAndDeletedAtIsNullAndNumberLessThanOrderByNumberDesc(
                storyId, ChapterStatus.PUBLISHED, chapter.getNumber())
            .map(Chapter::getId)
            .orElse(null);
    Long next =
        chapterRepository
            .findFirstByStory_IdAndStatusAndDeletedAtIsNullAndNumberGreaterThanOrderByNumberAsc(
                storyId, ChapterStatus.PUBLISHED, chapter.getNumber())
            .map(Chapter::getId)
            .orElse(null);
    return ChapterResponse.from(chapter, previous, next);
  }

  private Story requireOwnedStoryForUpdate(Long userId, Long storyId) {
    contentAccess.requireVerifiedAuthor(userId);
    Story story = storyRepository.findByIdForUpdate(storyId).orElseThrow(contentAccess::notFound);
    if (!story.getAuthor().getId().equals(userId)) {
      throw contentAccess.notFound();
    }
    return story;
  }

  public Chapter requireOwnedChapter(Long userId, Long chapterId) {
    contentAccess.requireVerifiedAuthor(userId);
    Chapter chapter =
        chapterRepository
            .findByIdAndDeletedAtIsNull(chapterId)
            .orElseThrow(contentAccess::notFound);
    if (!chapter.getStory().getAuthor().getId().equals(userId)) {
      throw contentAccess.notFound();
    }
    return chapter;
  }

  private Chapter requireOwnedChapterForUpdate(Long userId, Long chapterId) {
    contentAccess.requireVerifiedAuthor(userId);
    Chapter reference =
        chapterRepository
            .findByIdAndDeletedAtIsNull(chapterId)
            .orElseThrow(contentAccess::notFound);
    Story story =
        storyRepository
            .findByIdForUpdate(reference.getStory().getId())
            .orElseThrow(contentAccess::notFound);
    if (!story.getAuthor().getId().equals(userId)) {
      throw contentAccess.notFound();
    }
    Chapter chapter =
        chapterRepository.findByIdForUpdate(chapterId).orElseThrow(contentAccess::notFound);
    if (!chapter.getStory().getAuthor().getId().equals(userId)) {
      throw contentAccess.notFound();
    }
    return chapter;
  }

  private void verifyVersion(Chapter chapter, int expectedVersion) {
    if (chapter.getVersion() != expectedVersion) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "CHAPTER_VERSION_CONFLICT",
          "This chapter changed elsewhere. Reload it before saving again.");
    }
  }

  private String cleanNote(String note) {
    if (note == null || note.isBlank()) {
      return null;
    }
    String trimmed = note.strip();
    if (trimmed.length() > 2000) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "AUTHOR_NOTE_TOO_LONG",
          "Author notes cannot exceed 2,000 characters.");
    }
    return trimmed;
  }

  private PageResponse<ChapterSummary> mapPage(Page<Chapter> page) {
    return new PageResponse<>(
        page.getContent().stream()
            .map(chapter -> ChapterSummary.from(chapter, chapter.getTitle()))
            .toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
