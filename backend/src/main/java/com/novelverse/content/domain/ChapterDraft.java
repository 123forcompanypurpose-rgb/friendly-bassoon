package com.novelverse.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "chapter_drafts")
public class ChapterDraft {

  @Id
  @Column(name = "chapter_id")
  private Long chapterId;

  @MapsId
  @OneToOne
  @JoinColumn(name = "chapter_id")
  private Chapter chapter;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(name = "content_html", nullable = false, columnDefinition = "text")
  private String contentHtml = "";

  @Column(name = "author_note", length = 2000)
  private String authorNote;

  @Column(name = "base_version", nullable = false)
  private int baseVersion;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ChapterDraft() {}

  public ChapterDraft(
      Chapter chapter, String title, String contentHtml, String authorNote, int baseVersion) {
    this.chapter = chapter;
    this.title = title;
    this.contentHtml = contentHtml;
    this.authorNote = authorNote;
    this.baseVersion = baseVersion;
  }

  public void update(String title, String contentHtml, String authorNote, int baseVersion) {
    this.title = title;
    this.contentHtml = contentHtml;
    this.authorNote = authorNote;
    this.baseVersion = baseVersion;
  }

  public Long getChapterId() {
    return chapterId;
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

  public int getBaseVersion() {
    return baseVersion;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
