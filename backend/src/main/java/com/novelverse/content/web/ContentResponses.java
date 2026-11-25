package com.novelverse.content.web;

import com.novelverse.content.domain.Chapter;
import com.novelverse.content.domain.ChapterDraft;
import com.novelverse.content.domain.Genre;
import com.novelverse.content.domain.Story;
import com.novelverse.content.domain.Tag;
import java.time.Instant;
import java.util.List;

public final class ContentResponses {

  private ContentResponses() {}

  public record GenreResponse(short id, String name, String slug) {
    public static GenreResponse from(Genre genre) {
      return new GenreResponse(genre.getId(), genre.getName(), genre.getSlug());
    }
  }

  public record TagResponse(long id, String name, String slug, int useCount) {
    public static TagResponse from(Tag tag) {
      return new TagResponse(tag.getId(), tag.getName(), tag.getSlug(), tag.getUseCount());
    }
  }

  public record TagSummary(long id, String name, String slug) {
    public static TagSummary from(Tag tag) {
      return new TagSummary(tag.getId(), tag.getName(), tag.getSlug());
    }
  }

  public record PageResponse<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {}

  public record HomeResponse(
      List<StoryResponse> featured,
      List<StoryResponse> trending,
      List<StoryResponse> newReleases,
      List<StoryResponse> recentlyUpdated,
      List<StoryResponse> completed,
      List<GenreResponse> genres) {}

  public record StoryResponse(
      long id,
      String title,
      String slug,
      String blurb,
      String coverUrl,
      GenreResponse genre,
      String language,
      String maturity,
      String status,
      String visibility,
      int chapterCount,
      int wordCount,
      long viewCount,
      int voteCount,
      int commentCount,
      int libraryCount,
      double rating,
      int ratingCount,
      Instant publishedAt,
      Instant lastChapterAt,
      Instant createdAt,
      Instant updatedAt,
      AuthorSummary author,
      List<TagSummary> tags) {
    public static StoryResponse from(Story story) {
      return new StoryResponse(
          story.getId(),
          story.getTitle(),
          story.getSlug(),
          story.getBlurb(),
          story.getCoverUrl(),
          GenreResponse.from(story.getGenre()),
          story.getLanguage(),
          story.getMaturity().name(),
          story.getStatus().name(),
          story.getVisibility().name(),
          story.getChapterCount(),
          story.getWordCount(),
          story.getViewCount(),
          story.getVoteCount(),
          story.getCommentCount(),
          story.getLibraryCount(),
          story.getRatingCount() == 0 ? 0 : (double) story.getRatingSum() / story.getRatingCount(),
          story.getRatingCount(),
          story.getPublishedAt(),
          story.getLastChapterAt(),
          story.getCreatedAt(),
          story.getUpdatedAt(),
          new AuthorSummary(
              story.getAuthor().getId(),
              story.getAuthor().getUsername(),
              story.getAuthor().getDisplayName(),
              story.getAuthor().getAvatarUrl()),
          story.getTags().stream().map(TagSummary::from).toList());
    }
  }

  public record AuthorSummary(long id, String username, String displayName, String avatarUrl) {}

  public record ChapterResponse(
      long id,
      long storyId,
      int number,
      String title,
      String contentHtml,
      String authorNote,
      int wordCount,
      String status,
      Instant publishAt,
      Instant publishedAt,
      int version,
      Instant createdAt,
      Instant updatedAt,
      Long previousChapterId,
      Long nextChapterId,
      int readingTimeMinutes) {
    public static ChapterResponse from(Chapter chapter) {
      return from(chapter, null, null);
    }

    public static ChapterResponse from(
        Chapter chapter, Long previousChapterId, Long nextChapterId) {
      return new ChapterResponse(
          chapter.getId(),
          chapter.getStory().getId(),
          chapter.getNumber(),
          chapter.getTitle(),
          chapter.getContentHtml(),
          chapter.getAuthorNote(),
          chapter.getWordCount(),
          chapter.getStatus().name(),
          chapter.getPublishAt(),
          chapter.getPublishedAt(),
          chapter.getVersion(),
          chapter.getCreatedAt(),
          chapter.getUpdatedAt(),
          previousChapterId,
          nextChapterId,
          readingTimeMinutes(chapter.getWordCount()));
    }

    public static ChapterResponse fromDraft(Chapter chapter, ChapterDraft draft, int wordCount) {
      return new ChapterResponse(
          chapter.getId(),
          chapter.getStory().getId(),
          chapter.getNumber(),
          draft.getTitle(),
          draft.getContentHtml(),
          draft.getAuthorNote(),
          wordCount,
          chapter.getStatus().name(),
          chapter.getPublishAt(),
          chapter.getPublishedAt(),
          chapter.getVersion(),
          chapter.getCreatedAt(),
          chapter.getUpdatedAt(),
          null,
          null,
          readingTimeMinutes(wordCount));
    }

    private static int readingTimeMinutes(int wordCount) {
      return Math.max(1, (int) Math.ceil(wordCount / 200.0));
    }
  }

  public record ChapterSummary(
      long id,
      int number,
      String title,
      int wordCount,
      String status,
      Instant publishAt,
      Instant publishedAt) {
    public static ChapterSummary from(Chapter chapter, String title) {
      return new ChapterSummary(
          chapter.getId(),
          chapter.getNumber(),
          title,
          chapter.getWordCount(),
          chapter.getStatus().name(),
          chapter.getPublishAt(),
          chapter.getPublishedAt());
    }
  }
}
