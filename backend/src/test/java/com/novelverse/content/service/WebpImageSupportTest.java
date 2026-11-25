package com.novelverse.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class WebpImageSupportTest {

  @Test
  void webpImageIoCanEncodeAndDecodeCoverImages() throws Exception {
    BufferedImage source = new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();

    assertThat(ImageIO.write(source, "webp", output)).isTrue();
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));

    assertThat(decoded).isNotNull();
    assertThat(decoded.getWidth()).isEqualTo(12);
    assertThat(decoded.getHeight()).isEqualTo(8);
  }
}
