package com.novelverse.storage.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "app.storage.s3.enabled", havingValue = "true")
public class S3StorageConfig {

  @Bean
  S3Client s3Client(
      @Value("${app.storage.s3.endpoint}") String endpoint,
      @Value("${app.storage.s3.region}") String region,
      @Value("${app.storage.s3.access-key}") String accessKey,
      @Value("${app.storage.s3.secret-key}") String secretKey) {
    requireConfiguration(endpoint, accessKey, secretKey);
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(credentials(accessKey, secretKey))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  @Bean
  S3Presigner s3Presigner(
      @Value("${app.storage.s3.endpoint}") String endpoint,
      @Value("${app.storage.s3.region}") String region,
      @Value("${app.storage.s3.access-key}") String accessKey,
      @Value("${app.storage.s3.secret-key}") String secretKey) {
    requireConfiguration(endpoint, accessKey, secretKey);
    return S3Presigner.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(credentials(accessKey, secretKey))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
        .build();
  }

  private StaticCredentialsProvider credentials(String accessKey, String secretKey) {
    return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
  }

  private void requireConfiguration(String endpoint, String accessKey, String secretKey) {
    if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
      throw new IllegalStateException(
          "S3_ENDPOINT, S3_ACCESS_KEY, and S3_SECRET_KEY are required when S3_ENABLED=true.");
    }
  }
}
