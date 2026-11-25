package com.novelverse.content.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChapterDraftRepository extends JpaRepository<ChapterDraft, Long> {

  Optional<ChapterDraft> findByChapterId(Long chapterId);

  List<ChapterDraft> findByChapterIdIn(Collection<Long> chapterIds);
}
