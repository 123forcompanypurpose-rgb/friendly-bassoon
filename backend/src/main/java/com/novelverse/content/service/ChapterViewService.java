package com.novelverse.content.service;

import com.novelverse.content.domain.Chapter;
import com.novelverse.content.domain.ChapterRepository;
import com.novelverse.content.domain.ChapterStatus;
import com.novelverse.content.domain.Story;
import com.novelverse.content.domain.StoryVisibility;
import com.novelverse.security.IpHashService;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterViewService {

  private static final Pattern BOT_USER_AGENT =
      Pattern.compile(
          "(bot|crawler|spider|slurp|headless|lighthouse|preview)", Pattern.CASE_INSENSITIVE);

  private final ChapterRepository chapterRepository;
  private final ContentAccess contentAccess;
  private final IpHashService ipHashService;
  private final JdbcTemplate jdbcTemplate;

  public ChapterViewService(
      ChapterRepository chapterRepository,
      ContentAccess contentAccess,
      IpHashService ipHashService,
      JdbcTemplate jdbcTemplate) {
    this.chapterRepository = chapterRepository;
    this.contentAccess = contentAccess;
    this.ipHashService = ipHashService;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void record(long chapterId, Long userId, String remoteAddress, String userAgent) {
    Chapter chapter =
        chapterRepository
            .findByIdAndDeletedAtIsNull(chapterId)
            .orElseThrow(contentAccess::notFound);
    Story story = chapter.getStory();
    if (chapter.getStatus() != ChapterStatus.PUBLISHED
        || story.getDeletedAt() != null
        || (story.getVisibility() != StoryVisibility.PUBLISHED
            && story.getVisibility() != StoryVisibility.UNLISTED)
        || !contentAccess.canReadStory(story, userId)) {
      throw contentAccess.notFound();
    }

    if (userAgent != null && BOT_USER_AGENT.matcher(userAgent).find()) {
      return;
    }

    String viewer = userId == null ? "ip:" + remoteAddress : "user:" + userId;
    String viewerHash = ipHashService.hash(viewer);
    List<Long> accepted =
        jdbcTemplate.query(
            """
            INSERT INTO chapter_viewers (chapter_id, viewer_hash, last_viewed_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (chapter_id, viewer_hash) DO UPDATE
              SET last_viewed_at = CURRENT_TIMESTAMP
              WHERE chapter_viewers.last_viewed_at <= CURRENT_TIMESTAMP - INTERVAL '24 hours'
            RETURNING chapter_id
            """,
            (resultSet, rowNumber) -> resultSet.getLong(1),
            chapterId,
            viewerHash);

    if (!accepted.isEmpty()) {
      jdbcTemplate.update(
          "UPDATE chapters SET view_count = view_count + 1 WHERE id = ?", chapterId);
      jdbcTemplate.update(
          """
          UPDATE stories SET view_count = view_count + 1
          WHERE id = (SELECT story_id FROM chapters WHERE id = ?)
          """,
          chapterId);
    }
  }
}
