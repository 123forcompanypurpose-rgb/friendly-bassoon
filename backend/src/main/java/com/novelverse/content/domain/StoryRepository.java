package com.novelverse.content.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryRepository extends JpaRepository<Story, Long> {

  boolean existsBySlug(String slug);

  @EntityGraph(attributePaths = {"author", "genre"})
  @Query(
      """
      SELECT s FROM Story s
      WHERE s.deletedAt IS NULL
        AND s.visibility = com.novelverse.content.domain.StoryVisibility.PUBLISHED
        AND (:genreSlug IS NULL OR s.genre.slug = :genreSlug)
        AND (:includeMature = TRUE OR s.maturity <> com.novelverse.content.domain.StoryMaturity.MATURE)
      ORDER BY COALESCE(s.lastChapterAt, s.publishedAt) DESC, s.id DESC
      """)
  Page<Story> findPublicStories(
      @Param("genreSlug") String genreSlug,
      @Param("includeMature") boolean includeMature,
      Pageable pageable);

  @EntityGraph(attributePaths = {"author", "genre"})
  @Query(
      """
      SELECT s FROM Story s
      WHERE s.deletedAt IS NULL
        AND s.visibility = com.novelverse.content.domain.StoryVisibility.PUBLISHED
        AND (:genreSlug IS NULL OR s.genre.slug = :genreSlug)
        AND (:includeMature = TRUE OR s.maturity <> com.novelverse.content.domain.StoryMaturity.MATURE)
      ORDER BY s.viewCount DESC, s.voteCount DESC, s.id DESC
      """)
  Page<Story> findMostReadPublicStories(
      @Param("genreSlug") String genreSlug,
      @Param("includeMature") boolean includeMature,
      Pageable pageable);

  @EntityGraph(attributePaths = {"author", "genre"})
  @Query(
      """
      SELECT s FROM Story s
      WHERE s.deletedAt IS NULL
        AND s.visibility = com.novelverse.content.domain.StoryVisibility.PUBLISHED
        AND (:genreSlug IS NULL OR s.genre.slug = :genreSlug)
        AND (:includeMature = TRUE OR s.maturity <> com.novelverse.content.domain.StoryMaturity.MATURE)
      ORDER BY s.ratingCount DESC, s.ratingSum DESC, s.id DESC
      """)
  Page<Story> findTopRatedPublicStories(
      @Param("genreSlug") String genreSlug,
      @Param("includeMature") boolean includeMature,
      Pageable pageable);

  @EntityGraph(attributePaths = {"author", "genre"})
  @Query(
      """
      SELECT s FROM Story s
      WHERE s.deletedAt IS NULL
        AND s.visibility = com.novelverse.content.domain.StoryVisibility.PUBLISHED
        AND (:genreSlug IS NULL OR s.genre.slug = :genreSlug)
        AND (:includeMature = TRUE OR s.maturity <> com.novelverse.content.domain.StoryMaturity.MATURE)
      ORDER BY s.publishedAt DESC, s.id DESC
      """)
  Page<Story> findNewestPublicStories(
      @Param("genreSlug") String genreSlug,
      @Param("includeMature") boolean includeMature,
      Pageable pageable);

  @EntityGraph(attributePaths = {"author", "genre"})
  @Query(
      """
      SELECT s FROM Story s
      WHERE s.deletedAt IS NULL
        AND s.visibility = com.novelverse.content.domain.StoryVisibility.PUBLISHED
        AND s.featured = TRUE
        AND (:includeMature = TRUE OR s.maturity <> com.novelverse.content.domain.StoryMaturity.MATURE)
      ORDER BY COALESCE(s.lastChapterAt, s.publishedAt) DESC, s.id DESC
      """)
  Page<Story> findFeaturedPublicStories(
      @Param("includeMature") boolean includeMature, Pageable pageable);

  @EntityGraph(attributePaths = {"author", "genre"})
  @Query(
      """
      SELECT s FROM Story s
      WHERE s.deletedAt IS NULL
        AND s.visibility = com.novelverse.content.domain.StoryVisibility.PUBLISHED
        AND s.status = com.novelverse.content.domain.StoryStatus.COMPLETED
        AND (:includeMature = TRUE OR s.maturity <> com.novelverse.content.domain.StoryMaturity.MATURE)
      ORDER BY COALESCE(s.lastChapterAt, s.publishedAt) DESC, s.id DESC
      """)
  Page<Story> findCompletedPublicStories(
      @Param("includeMature") boolean includeMature, Pageable pageable);

  @EntityGraph(attributePaths = {"author", "genre"})
  Optional<Story> findByIdAndDeletedAtIsNull(Long id);

  @EntityGraph(attributePaths = {"author", "genre"})
  Page<Story> findByAuthor_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(
      Long authorId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Story s WHERE s.id = :id AND s.deletedAt IS NULL")
  Optional<Story> findByIdForUpdate(@Param("id") Long id);
}
