package com.novelverse.content.service;

import com.novelverse.common.ApiException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HtmlContentSanitizer {

  public record SanitizedContent(String html, int wordCount) {}

  private static final int MAX_WORDS = 50_000;

  private final PolicyFactory policy;

  public HtmlContentSanitizer(@Value("${app.storage.s3.public-base-url:}") String publicBaseUrl) {
    URI trustedImageBase = publicBaseUrl.isBlank() ? null : URI.create(publicBaseUrl);
    if (trustedImageBase != null
        && !"https".equalsIgnoreCase(trustedImageBase.getScheme())
        && !("http".equalsIgnoreCase(trustedImageBase.getScheme())
            && isLoopback(trustedImageBase.getHost()))) {
      throw new IllegalStateException(
          "S3_PUBLIC_BASE_URL must use HTTPS except for local development.");
    }
    List<String> protocols = new ArrayList<>(List.of("https", "mailto"));
    if (trustedImageBase != null && "http".equalsIgnoreCase(trustedImageBase.getScheme())) {
      protocols.add("http");
    }

    policy =
        new HtmlPolicyBuilder()
            .allowElements(
                "p",
                "br",
                "strong",
                "b",
                "em",
                "i",
                "u",
                "s",
                "strike",
                "blockquote",
                "pre",
                "code",
                "ul",
                "ol",
                "li",
                "h2",
                "h3",
                "h4",
                "hr",
                "a",
                "img")
            .allowAttributes("href")
            .onElements("a")
            .allowAttributes("alt")
            .onElements("img")
            .allowAttributes("src")
            .matching(
                (elementName, attributeName, value) -> isTrustedImage(value, trustedImageBase))
            .onElements("img")
            .allowUrlProtocols(protocols.toArray(String[]::new))
            .requireRelNofollowOnLinks()
            .toFactory();
  }

  public SanitizedContent sanitize(String source) {
    String safeHtml = policy.sanitize(source == null ? "" : source);
    String plainText = Jsoup.parseBodyFragment(safeHtml).text().strip();
    int wordCount = plainText.isEmpty() ? 0 : plainText.split("\\s+").length;
    if (wordCount > MAX_WORDS) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "CONTENT_TOO_LONG",
          "Chapter content cannot exceed 50,000 words.");
    }
    return new SanitizedContent(safeHtml, wordCount);
  }

  private String isTrustedImage(String value, URI trustedBase) {
    if (trustedBase == null) {
      return null;
    }
    try {
      URI image = URI.create(value);
      String basePath = trustedBase.getPath() == null ? "" : trustedBase.getPath();
      String imagePath = image.getPath() == null ? "" : image.getPath();
      if (trustedBase.getScheme().equalsIgnoreCase(image.getScheme())
          && trustedBase.getHost() != null
          && trustedBase.getHost().equalsIgnoreCase(image.getHost())
          && effectivePort(trustedBase) == effectivePort(image)
          && imagePath.startsWith(basePath.endsWith("/") ? basePath : basePath + "/")
          && image.getUserInfo() == null) {
        return value;
      }
    } catch (IllegalArgumentException ignored) {
      return null;
    }
    return null;
  }

  private int effectivePort(URI uri) {
    return uri.getPort() == -1
        ? ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443)
        : uri.getPort();
  }

  private boolean isLoopback(String host) {
    return host != null
        && (host.equalsIgnoreCase("localhost")
            || host.equals("127.0.0.1")
            || host.equals("::1")
            || host.equals("[::1]"));
  }
}
