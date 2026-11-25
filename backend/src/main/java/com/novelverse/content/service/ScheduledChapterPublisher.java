package com.novelverse.content.service;

import com.novelverse.content.domain.ChapterRepository;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledChapterPublisher {

  private static final Logger logger = LoggerFactory.getLogger(ScheduledChapterPublisher.class);

  private final ChapterRepository chapterRepository;
  private final ChapterService chapterService;

  public ScheduledChapterPublisher(
      ChapterRepository chapterRepository, ChapterService chapterService) {
    this.chapterRepository = chapterRepository;
    this.chapterService = chapterService;
  }

  @Scheduled(cron = "0 * * * * *")
  @SchedulerLock(
      name = "publishScheduledChapters",
      lockAtMostFor = "PT10M",
      lockAtLeastFor = "PT5S")
  public void publishDueChapters() {
    List<Long> dueChapterIds =
        chapterRepository.findDueChapterIds(Instant.now(), PageRequest.of(0, 100));
    for (Long chapterId : dueChapterIds) {
      try {
        chapterService.publishScheduled(chapterId, Instant.now());
      } catch (RuntimeException exception) {
        logger.error("Failed to publish scheduled chapter id={}", chapterId, exception);
      }
    }
  }
}
