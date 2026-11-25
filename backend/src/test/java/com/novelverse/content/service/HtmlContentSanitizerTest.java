package com.novelverse.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.novelverse.common.ApiException;
import org.junit.jupiter.api.Test;

class HtmlContentSanitizerTest {

  @Test
  void removesScriptsAndUnsafeLinksWhileKeepingAllowedFormatting() {
    HtmlContentSanitizer sanitizer = new HtmlContentSanitizer("");

    HtmlContentSanitizer.SanitizedContent content =
        sanitizer.sanitize(
            "<p>Hello <strong>writer</strong>.</p>"
                + "<script>hidden harmful</script>"
                + "<a href=\"javascript:alert(1)\">unsafe</a>");

    assertThat(content.html()).contains("<strong>writer</strong>");
    assertThat(content.html()).doesNotContain("script", "hidden", "javascript:");
    assertThat(content.wordCount()).isEqualTo(3);
  }

  @Test
  void allowsImagesOnlyFromConfiguredPublicStoragePath() {
    HtmlContentSanitizer sanitizer =
        new HtmlContentSanitizer("https://cdn.example.test/novelverse");

    String safeHtml =
        sanitizer
            .sanitize(
                "<img src=\"https://cdn.example.test/novelverse/covers/a.webp\" alt=\"cover\">"
                    + "<img src=\"https://attacker.example/image.webp\">")
            .html();

    assertThat(safeHtml).contains("https://cdn.example.test/novelverse/covers/a.webp");
    assertThat(safeHtml).doesNotContain("attacker.example");
  }

  @Test
  void rejectsContentAboveTheWordLimit() {
    HtmlContentSanitizer sanitizer = new HtmlContentSanitizer("");
    String overLimit = "<p>" + "word ".repeat(50_001) + "</p>";

    assertThatThrownBy(() -> sanitizer.sanitize(overLimit))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("50,000 words");
  }
}
