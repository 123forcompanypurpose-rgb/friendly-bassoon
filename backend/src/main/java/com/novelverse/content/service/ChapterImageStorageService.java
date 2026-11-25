package com.novelverse.content.service;

import com.novelverse.common.ApiException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class ChapterImageStorageService {

  public record UploadLink(String uploadUrl, String objectKey, Instant expiresAt) {}

  public record ImageResult(String imageUrl) {}

  private static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

  private final ObjectProvider<S3Client> s3ClientProvider;
  private final ObjectProvider<S3Presigner> presignerProvider;
  private final ChapterService chapterService;
  private final String bucket;
  private final String publicBaseUrl;
  private final boolean enabled;

  public ChapterImageStorageService(
      ObjectProvider<S3Client> s3ClientProvider,
      ObjectProvider<S3Presigner> presignerProvider,
      ChapterService chapterService,
      @Value("${app.storage.s3.bucket}") String bucket,
      @Value("${app.storage.s3.public-base-url}") String publicBaseUrl,
      @Value("${app.storage.s3.enabled}") boolean enabled) {
    this.s3ClientProvider = s3ClientProvider;
    this.presignerProvider = presignerProvider;
    this.chapterService = chapterService;
    this.bucket = bucket;
    this.publicBaseUrl = publicBaseUrl;
    this.enabled = enabled;
  }

  public UploadLink createUploadLink(
      Long userId, Long chapterId, String contentType, long contentLength) {
    ensureEnabled();
    chapterService.requireOwnedChapter(userId, chapterId);
    validateContentType(contentType);
    if (contentLength < 1 || contentLength > MAX_BYTES) {
      throw invalidUpload();
    }

    String key = "chapter-image-staging/" + userId + "/" + chapterId + "/" + UUID.randomUUID();
    PresignedPutObjectRequest presigned =
        presignerProvider
            .getObject()
            .presignPutObject(
                PutObjectPresignRequest.builder()
                    .signatureDuration(PRESIGN_TTL)
                    .putObjectRequest(
                        software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build())
                    .build());
    return new UploadLink(presigned.url().toString(), key, Instant.now().plus(PRESIGN_TTL));
  }

  public ImageResult confirmUpload(Long userId, Long chapterId, String objectKey) {
    ensureEnabled();
    chapterService.requireOwnedChapter(userId, chapterId);
    String requiredPrefix = "chapter-image-staging/" + userId + "/" + chapterId + "/";
    if (objectKey == null || !objectKey.startsWith(requiredPrefix) || objectKey.length() > 250) {
      throw invalidUpload();
    }

    try {
      UUID.fromString(objectKey.substring(requiredPrefix.length()));
    } catch (IllegalArgumentException exception) {
      throw invalidUpload();
    }

    S3Client client = s3ClientProvider.getObject();
    try {
      var metadata =
          client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
      if (metadata.contentLength() < 1 || metadata.contentLength() > MAX_BYTES) {
        throw invalidUpload();
      }
      validateContentType(metadata.contentType());

      byte[] bytes;
      try (ResponseInputStream<GetObjectResponse> response =
          client.getObject(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())) {
        bytes = response.readAllBytes();
      }
      if (!hasMatchingMagic(bytes, metadata.contentType())) {
        throw invalidUpload();
      }

      String finalKey = "chapter-images/" + userId + "/" + chapterId + "/" + UUID.randomUUID();
      client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(finalKey)
              .contentType(metadata.contentType())
              .contentLength((long) bytes.length)
              .cacheControl("public, max-age=31536000, immutable")
              .build(),
          RequestBody.fromBytes(bytes));
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
      return new ImageResult(publicUrl(finalKey));
    } catch (S3Exception | IOException exception) {
      throw invalidUpload();
    }
  }

  private void ensureEnabled() {
    if (!enabled || publicBaseUrl == null || publicBaseUrl.isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_DISABLED", "Image storage is unavailable.");
    }
  }

  private void validateContentType(String contentType) {
    if (!"image/jpeg".equals(contentType)
        && !"image/png".equals(contentType)
        && !"image/webp".equals(contentType)) {
      throw invalidUpload();
    }
  }

  private boolean hasMatchingMagic(byte[] bytes, String contentType) {
    if (bytes.length < 12) return false;
    if ("image/jpeg".equals(contentType)) {
      return (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }
    if ("image/png".equals(contentType)) {
      return (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
    }
    return bytes[0] == 'R'
        && bytes[1] == 'I'
        && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W'
        && bytes[9] == 'E'
        && bytes[10] == 'B'
        && bytes[11] == 'P';
  }

  private String publicUrl(String key) {
    return publicBaseUrl.replaceAll("/+$", "") + "/" + key;
  }

  private ApiException invalidUpload() {
    return new ApiException(
        HttpStatus.BAD_REQUEST, "INVALID_IMAGE_UPLOAD", "The uploaded image is invalid.");
  }
}
