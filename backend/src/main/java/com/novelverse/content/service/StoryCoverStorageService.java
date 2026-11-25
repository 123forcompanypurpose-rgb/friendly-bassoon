package com.novelverse.content.service;

import com.novelverse.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
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
public class StoryCoverStorageService {

  public record UploadLink(String uploadUrl, String objectKey, Instant expiresAt) {}

  public record CoverResult(String coverUrl) {}

  private static final long MAX_BYTES = 2L * 1024 * 1024;
  private static final long MAX_PIXELS = 25_000_000L;
  private static final int MAX_DIMENSION = 10_000;
  private static final int OUTPUT_DIMENSION = 800;
  private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);
  private static final String WEBP = "image/webp";
  private static final String JPEG = "image/jpeg";
  private static final String PNG = "image/png";

  private final ObjectProvider<S3Client> s3ClientProvider;
  private final ObjectProvider<S3Presigner> presignerProvider;
  private final StoryService storyService;
  private final String bucket;
  private final String publicBaseUrl;
  private final boolean enabled;

  public StoryCoverStorageService(
      ObjectProvider<S3Client> s3ClientProvider,
      ObjectProvider<S3Presigner> presignerProvider,
      StoryService storyService,
      @Value("${app.storage.s3.bucket}") String bucket,
      @Value("${app.storage.s3.public-base-url}") String publicBaseUrl,
      @Value("${app.storage.s3.enabled}") boolean enabled) {
    this.s3ClientProvider = s3ClientProvider;
    this.presignerProvider = presignerProvider;
    this.storyService = storyService;
    this.bucket = bucket;
    this.publicBaseUrl = publicBaseUrl;
    this.enabled = enabled;
  }

  public UploadLink createUploadLink(
      Long userId, Long storyId, String contentType, long contentLength) {
    ensureEnabled();
    storyService.requireOwnedStory(userId, storyId);
    validateContentType(contentType);
    validateSize(contentLength);

    String key = "story-cover-staging/" + userId + "/" + storyId + "/" + UUID.randomUUID();
    PresignedPutObjectRequest presigned =
        presignerProvider
            .getObject()
            .presignPutObject(
                PutObjectPresignRequest.builder()
                    .signatureDuration(PRESIGN_TTL)
                    .putObjectRequest(
                        PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build())
                    .build());
    return new UploadLink(presigned.url().toString(), key, Instant.now().plus(PRESIGN_TTL));
  }

  public CoverResult confirmUpload(Long userId, Long storyId, String objectKey) {
    ensureEnabled();
    storyService.requireOwnedStory(userId, storyId);
    String requiredPrefix = "story-cover-staging/" + userId + "/" + storyId + "/";
    if (objectKey == null || !objectKey.startsWith(requiredPrefix) || objectKey.length() > 250) {
      throw invalidUpload();
    }
    String uploadId = objectKey.substring(requiredPrefix.length());
    try {
      UUID parsedId = UUID.fromString(uploadId);
      if (!parsedId.toString().equals(uploadId)) {
        throw invalidUpload();
      }
    } catch (IllegalArgumentException exception) {
      throw invalidUpload();
    }

    S3Client client = s3ClientProvider.getObject();
    try {
      var metadata =
          client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
      validateSize(metadata.contentLength());
      validateContentType(metadata.contentType());
      byte[] uploadedBytes = readUpload(client, objectKey);
      BufferedImage image = decodeImage(uploadedBytes, metadata.contentType());
      byte[] normalizedImage = normalizeImage(image);

      String finalKey =
          "story-covers/" + userId + "/" + storyId + "/" + UUID.randomUUID() + ".webp";
      client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(finalKey)
              .contentType(WEBP)
              .contentLength((long) normalizedImage.length)
              .cacheControl("public, max-age=31536000, immutable")
              .build(),
          RequestBody.fromBytes(normalizedImage));
      String coverUrl = publicUrl(finalKey);
      storyService.updateCover(userId, storyId, coverUrl);
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
      return new CoverResult(coverUrl);
    } catch (SdkException exception) {
      if (exception instanceof S3Exception s3Exception && s3Exception.statusCode() == 404) {
        throw new ApiException(
            HttpStatus.BAD_REQUEST,
            "COVER_UPLOAD_NOT_FOUND",
            "Upload the cover image before confirming it.");
      }
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "COVER_STORAGE_FAILED",
          "Cover image storage is temporarily unavailable.");
    }
  }

  private byte[] readUpload(S3Client client, String key) {
    try (ResponseInputStream<GetObjectResponse> input =
        client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
      byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES || input.read() != -1) {
        throw invalidUpload();
      }
      return bytes;
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "COVER_STORAGE_FAILED",
          "Cover image storage is temporarily unavailable.");
    }
  }

  private BufferedImage decodeImage(byte[] bytes, String declaredType) {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      if (input == null) {
        throw invalidUpload();
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw invalidUpload();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName();
        boolean valid =
            ("JPEG".equalsIgnoreCase(format) && JPEG.equalsIgnoreCase(declaredType))
                || ("PNG".equalsIgnoreCase(format) && PNG.equalsIgnoreCase(declaredType))
                || ("WEBP".equalsIgnoreCase(format) && WEBP.equalsIgnoreCase(declaredType));
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (!valid
            || width <= 0
            || height <= 0
            || width > MAX_DIMENSION
            || height > MAX_DIMENSION
            || (long) width * height > MAX_PIXELS) {
          throw invalidUpload();
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          throw invalidUpload();
        }
        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw invalidUpload();
    }
  }

  private byte[] normalizeImage(BufferedImage source) {
    double scale =
        Math.min(1.0, (double) OUTPUT_DIMENSION / Math.max(source.getWidth(), source.getHeight()));
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = output.createGraphics();
    try {
      graphics.setColor(Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      if (!ImageIO.write(output, "webp", bytes)) {
        throw new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COVER_ENCODING_FAILED",
            "Could not process the cover image.");
      }
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "COVER_ENCODING_FAILED",
          "Could not process the cover image.");
    }
  }

  private void validateContentType(String contentType) {
    if (contentType == null
        || (!JPEG.equalsIgnoreCase(contentType)
            && !PNG.equalsIgnoreCase(contentType)
            && !WEBP.equalsIgnoreCase(contentType))) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "COVER_UNSUPPORTED_TYPE",
          "Cover images must be JPEG, PNG, or WebP.");
    }
  }

  private void validateSize(long contentLength) {
    if (contentLength < 1 || contentLength > MAX_BYTES) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "COVER_INVALID_SIZE",
          "Cover images must be no larger than 2 MB.");
    }
  }

  private String publicUrl(String key) {
    if (publicBaseUrl.isBlank()) {
      throw new IllegalStateException("S3_PUBLIC_BASE_URL is required for public cover URLs.");
    }
    return URI.create(publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/")
        .resolve(key)
        .toString();
  }

  private void ensureEnabled() {
    if (!enabled || publicBaseUrl.isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "COVER_STORAGE_UNAVAILABLE",
          "Cover image storage is not fully configured.");
    }
  }

  private ApiException invalidUpload() {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "COVER_INVALID_IMAGE",
        "The uploaded file is not a valid supported image.");
  }
}
