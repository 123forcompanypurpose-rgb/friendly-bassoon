package com.novelverse.content.domain;

import com.novelverse.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "stories")
public class Story {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private UserAccount author;

  @Column(nullable = false, length = 120)
  private String title;

  @Column(nullable = false, length = 140)
  private String slug;

  @Column(nullable = false, length = 2000)
  private String blurb = "";

  @Column(name = "cover_url", length = 500)
  private String coverUrl;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "genre_id", nullable = false)
  private Genre genre;

  @Column(nullable = false, length = 10)
  private String language = "en";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private StoryMaturity maturity = StoryMaturity.EVERYONE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private StoryStatus status = StoryStatus.ONGOING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private StoryVisibility visibility = StoryVisibility.DRAFT;

  @Column(name = "is_featured", nullable = false)
  private boolean featured;

  @Column(name = "chapter_count", nullable = false)
  private int chapterCount;

  @Column(name = "word_count", nullable = false)
  private int wordCount;

  @Column(name = "view_count", nullable = false)
  private long viewCount;

  @Column(name = "vote_count", nullable = false)
  private int voteCount;

  @Column(name = "comment_count", nullable = false)
  private int commentCount;

  @Column(name = "library_count", nullable = false)
  private int libraryCount;

  @Column(name = "rating_sum", nullable = false)
  private int ratingSum;

  @Column(name = "rating_count", nullable = false)
  private int ratingCount;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_chapter_at")
  private Instant lastChapterAt;

  @Column(name = "removed_reason", length = 500)
  private String removedReason;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @BatchSize(size = 50)
  @JoinTable(
      name = "story_tags",
      joinColumns = @JoinColumn(name = "story_id"),
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @OrderBy("name ASC")
  private Set<Tag> tags = new LinkedHashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Story() {}

  public Story(
      UserAccount author,
      String title,
      String slug,
      String blurb,
      Genre genre,
      String language,
      StoryMaturity maturity,
      StoryStatus status) {
    this.author = author;
    this.title = title;
    this.slug = slug;
    this.blurb = blurb;
    this.genre = genre;
    this.language = language;
    this.maturity = maturity;
    this.status = status;
  }

  public void update(
      String title,
      String slug,
      String blurb,
      Genre genre,
      String language,
      StoryMaturity maturity,
      StoryStatus status) {
    this.title = title;
    this.slug = slug;
    this.blurb = blurb;
    this.genre = genre;
    this.language = language;
    this.maturity = maturity;
    this.status = status;
  }

  public void setTags(Set<Tag> tags) {
    this.tags = new LinkedHashSet<>(tags);
  }

  public void setCoverUrl(String coverUrl) {
    this.coverUrl = coverUrl;
  }

  public void publish(Instant now) {
    visibility = StoryVisibility.PUBLISHED;
    if (publishedAt == null) {
      publishedAt = now;
    }
  }

  public void unpublish() {
    visibility = StoryVisibility.DRAFT;
  }

  public void setUnlisted() {
    visibility = StoryVisibility.UNLISTED;
  }

  public void markDeleted(Instant now) {
    visibility = StoryVisibility.REMOVED;
    deletedAt = now;
  }

  public void updatePublishedCounts(int chapterCount, int wordCount, Instant lastChapterAt) {
    this.chapterCount = chapterCount;
    this.wordCount = wordCount;
    this.lastChapterAt = lastChapterAt;
  }

  public Long getId() {
    return id;
  }

  public UserAccount getAuthor() {
    return author;
  }

  public String getTitle() {
    return title;
  }

  public String getSlug() {
    return slug;
  }

  public String getBlurb() {
    return blurb;
  }

  public String getCoverUrl() {
    return coverUrl;
  }

  public Genre getGenre() {
    return genre;
  }

  public String getLanguage() {
    return language;
  }

  public StoryMaturity getMaturity() {
    return maturity;
  }

  public StoryStatus getStatus() {
    return status;
  }

  public StoryVisibility getVisibility() {
    return visibility;
  }

  public int getChapterCount() {
    return chapterCount;
  }

  public int getWordCount() {
    return wordCount;
  }

  public long getViewCount() {
    return viewCount;
  }

  public int getVoteCount() {
    return voteCount;
  }

  public int getCommentCount() {
    return commentCount;
  }

  public int getLibraryCount() {
    return libraryCount;
  }

  public int getRatingSum() {
    return ratingSum;
  }

  public int getRatingCount() {
    return ratingCount;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public Instant getLastChapterAt() {
    return lastChapterAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Set<Tag> getTags() {
    return tags;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
