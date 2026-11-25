package com.novelverse.content.web;

import com.novelverse.content.domain.StoryMaturity;
import com.novelverse.content.domain.StoryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ContentRequests {

  private ContentRequests() {}

  public record CreateStoryRequest(
      @NotBlank @Size(min = 3, max = 120) String title,
      @NotNull Short genreId,
      @Pattern(regexp = "^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*$") @Size(max = 10) String language,
      StoryMaturity maturity,
      StoryStatus status,
      @Size(max = 2000) String blurb,
      @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags) {}

  public record UpdateStoryRequest(
      @Size(min = 3, max = 120) String title,
      Short genreId,
      @Pattern(regexp = "^[A-Za-z]{2,3}([_-][A-Za-z0-9]{2,8})*$") @Size(max = 10) String language,
      StoryMaturity maturity,
      StoryStatus status,
      @Size(max = 2000) String blurb,
      @Size(max = 10) List<@NotBlank @Size(max = 40) String> tags) {}

  public record CreateChapterRequest(
      @NotBlank @Size(max = 150) String title,
      @Size(max = 1_000_000) String contentHtml,
      @Size(max = 2000) String authorNote) {}

  public record SaveChapterDraftRequest(
      @NotNull @Min(0) Integer expectedVersion,
      @NotBlank @Size(max = 150) String title,
      @NotNull @Size(max = 1_000_000) String contentHtml,
      @Size(max = 2000) String authorNote) {}

  public record PublishChapterRequest(@NotNull @Min(0) Integer expectedVersion) {}

  public record ScheduleChapterRequest(
      @NotNull @Min(0) Integer expectedVersion, @NotNull Instant publishAt) {}

  public record ReorderChaptersRequest(
      @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> chapterIds) {}

  public record PresignCoverRequest(
      @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)") String contentType,
      @Min(1) @Max(2_097_152) long contentLength) {}

  public record ConfirmCoverRequest(@NotBlank @Size(max = 250) String objectKey) {}
}
