package com.novelverse.content.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {

  @Query(
      """
      SELECT COALESCE(MAX(c.number), 0) FROM Chapter c
      WHERE c.story.id = :storyId AND c.deletedAt IS NULL
      """)
  int findMaxActiveNumber(@Param("storyId") Long storyId);

  List<Chapter> findByStory_IdAndDeletedAtIsNullOrderByNumberAsc(Long storyId);

  Page<Chapter> findByStory_IdAndDeletedAtIsNullOrderByNumberAsc(Long storyId, Pageable pageable);

  Page<Chapter> findByStory_IdAndStatusAndDeletedAtIsNullOrderByNumberAsc(
      Long storyId, ChapterStatus status, Pageable pageable);

  Optional<Chapter> findByIdAndDeletedAtIsNull(Long chapterId);

  long countByStory_IdAndStatusAndDeletedAtIsNull(Long storyId, ChapterStatus status);

  Optional<Chapter>
      findFirstByStory_IdAndStatusAndDeletedAtIsNullAndNumberLessThanOrderByNumberDesc(
          Long storyId, ChapterStatus status, int number);

  Optional<Chapter>
      findFirstByStory_IdAndStatusAndDeletedAtIsNullAndNumberGreaterThanOrderByNumberAsc(
          Long storyId, ChapterStatus status, int number);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Chapter c WHERE c.id = :id AND c.deletedAt IS NULL")
  Optional<Chapter> findByIdForUpdate(@Param("id") Long id);

  @Query(
      """
      SELECT c.id FROM Chapter c
      WHERE c.status = com.novelverse.content.domain.ChapterStatus.SCHEDULED
        AND c.publishAt <= :now AND c.deletedAt IS NULL
      ORDER BY c.publishAt ASC
      """)
  List<Long> findDueChapterIds(@Param("now") Instant now, Pageable pageable);
}
