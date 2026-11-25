package com.novelverse.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novelverse.common.ApiException;
import com.novelverse.content.domain.GenreRepository;
import com.novelverse.content.domain.StoryMaturity;
import com.novelverse.content.domain.StoryStatus;
import com.novelverse.content.service.ChapterService;
import com.novelverse.content.service.StoryService;
import com.novelverse.content.web.ContentRequests.CreateChapterRequest;
import com.novelverse.content.web.ContentRequests.CreateStoryRequest;
import com.novelverse.content.web.ContentRequests.PublishChapterRequest;
import com.novelverse.content.web.ContentRequests.ReorderChaptersRequest;
import com.novelverse.content.web.ContentRequests.SaveChapterDraftRequest;
import com.novelverse.content.web.ContentRequests.ScheduleChapterRequest;
import com.novelverse.content.web.ContentRequests.UpdateStoryRequest;
import com.novelverse.content.web.ContentResponses.ChapterResponse;
import com.novelverse.content.web.ContentResponses.StoryResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ContentIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("novelverse")
          .withUsername("novelverse")
          .withPassword("novelverse");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("DB_URL", POSTGRES::getJdbcUrl);
    registry.add("DB_USER", POSTGRES::getUsername);
    registry.add("DB_PASSWORD", POSTGRES::getPassword);
  }

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private GenreRepository genreRepository;

  @Autowired private StoryService storyService;

  @Autowired private ChapterService chapterService;

  @Test
  void createsContentEnforcesOwnershipSanitizesDraftsAndPublishesForReaders() {
    long authorId = createVerifiedUser("author");
    long otherUserId = createVerifiedUser("reader");
    short genreId = genreRepository.findByActiveTrueOrderBySortOrderAscNameAsc().getFirst().getId();
    StoryResponse story =
        storyService.create(
            authorId,
            new CreateStoryRequest(
                "Phase Two Story " + UUID.randomUUID().toString().substring(0, 8),
                genreId,
                "en",
                StoryMaturity.MATURE,
                StoryStatus.ONGOING,
                "An <script>unsafe()</script> blurb.",
                List.of("fantasy", "slow-burn")));

    assertThat(story.slug()).startsWith("phase-two-story");
    assertThat(story.tags())
        .extracting(com.novelverse.content.web.ContentResponses.TagSummary::name)
        .containsExactlyInAnyOrder("fantasy", "slow-burn");
    assertThatThrownBy(() -> storyService.get(story.id(), otherUserId))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getStatus().value())
        .isEqualTo(404);
    assertThatThrownBy(
            () ->
                storyService.update(
                    otherUserId,
                    story.id(),
                    new UpdateStoryRequest(null, null, null, null, null, null, null)))
        .isInstanceOf(ApiException.class);

    ChapterResponse chapter =
        chapterService.create(
            authorId,
            story.id(),
            new CreateChapterRequest(
                "First chapter",
                "<p>First <strong>draft</strong>.</p><script>steal()</script>",
                null));
    assertThat(chapter.wordCount()).isEqualTo(2);
    ChapterResponse secondChapter =
        chapterService.create(
            authorId, story.id(), new CreateChapterRequest("Second chapter", "<p>Next.</p>", null));
    assertThatThrownBy(() -> chapterService.getDraft(otherUserId, chapter.id()))
        .isInstanceOf(ApiException.class);
    chapterService.reorder(
        authorId,
        story.id(),
        new ReorderChaptersRequest(List.of(secondChapter.id(), chapter.id())));

    ChapterResponse currentDraft = chapterService.getDraft(authorId, chapter.id());
    assertThat(currentDraft.number()).isEqualTo(2);
    ChapterResponse savedDraft =
        chapterService.saveDraft(
            authorId,
            chapter.id(),
            new SaveChapterDraftRequest(
                currentDraft.version(),
                "First chapter",
                "<p>Updated <em>draft</em>.</p><script>steal()</script>",
                null));
    assertThat(savedDraft.version()).isEqualTo(currentDraft.version() + 1);
    assertThat(savedDraft.contentHtml()).doesNotContain("script", "steal");
    assertThatThrownBy(
            () ->
                chapterService.saveDraft(
                    authorId,
                    chapter.id(),
                    new SaveChapterDraftRequest(
                        currentDraft.version(), "Stale edit", "stale", null)))
        .isInstanceOf(ApiException.class)
        .extracting(exception -> ((ApiException) exception).getCode())
        .isEqualTo("CHAPTER_VERSION_CONFLICT");

    chapterService.publish(authorId, chapter.id(), new PublishChapterRequest(savedDraft.version()));
    ChapterResponse secondDraft = chapterService.getDraft(authorId, secondChapter.id());
    chapterService.publish(
        authorId, secondChapter.id(), new PublishChapterRequest(secondDraft.version()));
    storyService.publish(authorId, story.id(), false);
    assertThatThrownBy(() -> storyService.get(story.id(), otherUserId))
        .isInstanceOf(ApiException.class);

    jdbcTemplate.update(
        "UPDATE users SET birth_date = DATE '1990-01-01', show_mature = TRUE WHERE id = ?",
        otherUserId);
    Instant publishAt = Instant.now().plusSeconds(120);
    ChapterResponse scheduled =
        chapterService.create(
            authorId, story.id(), new CreateChapterRequest("Scheduled", "<p>Later.</p>", null));
    chapterService.schedule(
        authorId, scheduled.id(), new ScheduleChapterRequest(scheduled.version(), publishAt));
    chapterService.publishScheduled(scheduled.id(), publishAt.plusSeconds(60));

    StoryResponse publicStory = storyService.get(story.id(), otherUserId);
    ChapterResponse publicChapter = chapterService.get(chapter.id(), otherUserId);

    assertThat(publicStory.chapterCount()).isEqualTo(3);
    assertThat(publicStory.wordCount()).isEqualTo(4);
    assertThat(publicChapter.previousChapterId()).isEqualTo(secondChapter.id());
    assertThat(publicChapter.nextChapterId()).isEqualTo(scheduled.id());
    assertThat(publicChapter.contentHtml()).contains("<em>draft</em>");
    assertThat(publicChapter.contentHtml()).doesNotContain("script", "steal");
    assertThat(publicChapter.readingTimeMinutes()).isEqualTo(1);
    assertThat(chapterService.get(secondChapter.id(), otherUserId).nextChapterId())
        .isEqualTo(chapter.id());
  }

  private long createVerifiedUser(String label) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO users (email, username, display_name, email_verified)
        VALUES (?, ?, ?, TRUE)
        RETURNING id
        """,
        Long.class,
        label + "-" + suffix + "@example.test",
        label + "_" + suffix,
        label);
  }
}
