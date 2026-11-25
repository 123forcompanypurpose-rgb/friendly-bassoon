package com.novelverse.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "chapters")
public class Chapter {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "story_id", nullable = false)
  private Story story;

  @Column(nullable = false)
  private int number;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(name = "content_html", nullable = false, columnDefinition = "text")
  private String contentHtml = "";

  @Column(name = "author_note", length = 2000)
  private String authorNote;

  @Column(name = "word_count", nullable = false)
  private int wordCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private ChapterStatus status = ChapterStatus.DRAFT;

  @Column(name = "publish_at")
  private Instant publishAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "is_premium", nullable = false)
  private boolean premium;

  @Column(name = "coin_price", nullable = false)
  private int coinPrice;

  @Column(name = "view_count", nullable = false)
  private long viewCount;

  @Column(name = "vote_count", nullable = false)
  private int voteCount;

  @Column(name = "comment_count", nullable = false)
  private int commentCount;

  @Version
  @Column(nullable = false)
  private int version;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Chapter() {}

  public Chapter(
      Story story, int number, String title, String contentHtml, String authorNote, int wordCount) {
    this.story = story;
    this.number = number;
    this.title = title;
    this.contentHtml = contentHtml;
    this.authorNote = authorNote;
    this.wordCount = wordCount;
  }

  public void updateNumber(int number) {
    this.number = number;
  }

  public void saveDraft(String title, String contentHtml, String authorNote, int wordCount) {
    this.title = title;
    this.contentHtml = contentHtml;
    this.authorNote = authorNote;
    this.wordCount = wordCount;
  }

  public void touchDraft() {
    updatedAt = Instant.now();
  }

  public void publish(Instant now) {
    status = ChapterStatus.PUBLISHED;
    publishAt = null;
    publishedAt = now;
  }

  public void schedule(Instant publishAt) {
    status = ChapterStatus.SCHEDULED;
    this.publishAt = publishAt;
  }

  public void unpublish() {
    status = ChapterStatus.DRAFT;
    publishAt = null;
    publishedAt = null;
  }

  public void markDeleted(Instant now) {
    status = ChapterStatus.REMOVED;
    deletedAt = now;
    publishAt = null;
    number = -number;
  }

  public Long getId() {
    return id;
  }

  public Story getStory() {
    return story;
  }

  public int getNumber() {
    return number;
  }

  public String getTitle() {
    return title;
  }

  public String getContentHtml() {
    return contentHtml;
  }

  public String getAuthorNote() {
    return authorNote;
  }

  public int getWordCount() {
    return wordCount;
  }

  public ChapterStatus getStatus() {
    return status;
  }

  public Instant getPublishAt() {
    return publishAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public int getVersion() {
    return version;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
