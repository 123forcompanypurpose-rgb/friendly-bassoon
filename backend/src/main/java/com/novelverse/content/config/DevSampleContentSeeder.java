package com.novelverse.content.config;

import java.util.regex.Pattern;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevSampleContentSeeder implements ApplicationRunner {

  private static final String AUTHOR_EMAIL = "library@novelverse.local";
  private static final String AUTHOR_USERNAME = "novelverse_library";
  private static final String STORY_SLUG = "the-violet-archive";
  private static final String CHAPTER_HTML =
      """
      <p>At the edge of the old city stood a library that opened only after midnight. Mara found its brass key beneath a loose floorboard, warm as if someone had just held it.</p>
      <p>Inside, every book hummed softly. The librarian, a silver-haired man with ink on his cuffs, told her that one volume had lost its ending. Without it, the city would forget the stars one by one.</p>
      <p>Mara had never been brave, but she knew how to listen. She followed the faintest song between the shelves, where a violet light flickered behind a row of atlases.</p>
      <p>When she opened the hidden book, the first missing word appeared on the page: <em>remember</em>.</p>
      """;

  private static final Pattern TAGS = Pattern.compile("<[^>]+>");

  private final JdbcTemplate jdbcTemplate;

  public DevSampleContentSeeder(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbcTemplate.update(
        """
        INSERT INTO users (email, username, display_name, email_verified, terms_accepted_at)
        VALUES (?, ?, 'NovelVerse Library', TRUE, CURRENT_TIMESTAMP)
        ON CONFLICT (email) DO NOTHING
        """,
        AUTHOR_EMAIL,
        AUTHOR_USERNAME);
    Long authorId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE email = ?", Long.class, AUTHOR_EMAIL);
    jdbcTemplate.update(
        """
        INSERT INTO stories (
          author_id, title, slug, blurb, genre_id, language, maturity, status,
          visibility, is_featured, published_at
        )
        SELECT ?, 'The Violet Archive', ?, ?, id, 'en', 'EVERYONE', 'ONGOING',
          'PUBLISHED', TRUE, CURRENT_TIMESTAMP
        FROM genres WHERE slug = 'fantasy'
        ON CONFLICT (slug) DO NOTHING
        """,
        authorId,
        STORY_SLUG,
        "A midnight librarian and a lost book must restore the stars to their city.");
    Long storyId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM stories WHERE slug = ?", Long.class, STORY_SLUG);
    jdbcTemplate.update(
        """
        INSERT INTO chapters (story_id, number, title, content_html, word_count, status, published_at)
        SELECT ?, 1, 'The Key After Midnight', ?, ?, 'PUBLISHED', CURRENT_TIMESTAMP
        WHERE NOT EXISTS (
          SELECT 1 FROM chapters WHERE story_id = ? AND number = 1 AND deleted_at IS NULL
        )
        """,
        storyId,
        CHAPTER_HTML,
        wordCount(CHAPTER_HTML),
        storyId);
    jdbcTemplate.update(
        """
        UPDATE stories SET
          chapter_count = (SELECT COUNT(*) FROM chapters WHERE story_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL),
          word_count = (SELECT COALESCE(SUM(word_count), 0) FROM chapters WHERE story_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL),
          last_chapter_at = (SELECT MAX(published_at) FROM chapters WHERE story_id = ? AND status = 'PUBLISHED' AND deleted_at IS NULL)
        WHERE id = ?
        """,
        storyId,
        storyId,
        storyId,
        storyId);
  }

  private int wordCount(String html) {
    String text = TAGS.matcher(html).replaceAll(" ").strip();
    return text.isEmpty() ? 0 : text.split("\\s+").length;
  }
}
