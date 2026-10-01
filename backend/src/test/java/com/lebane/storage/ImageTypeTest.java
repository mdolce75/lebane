package com.lebane.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.lebane.storage.service.ImageType;

class ImageTypeTest {

    @Test
    void detectsSupportedFormatsByContent() {
        assertThat(ImageType.detect(TestImages.PNG_HEADER)).contains(ImageType.PNG);
        assertThat(ImageType.detect(TestImages.JPEG_HEADER)).contains(ImageType.JPEG);
        assertThat(ImageType.detect(TestImages.WEBP_HEADER)).contains(ImageType.WEBP);
        assertThat(ImageType.detect(TestImages.realPng())).contains(ImageType.PNG);
    }

    @Test
    void rejectsOtherContentRegardlessOfName() {
        assertThat(ImageType.detect("<html><script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(ImageType.detect("GIF89a......".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageType.detect(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'})).isEmpty();
        assertThat(ImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }

    @Test
    void exposesContentTypeAndExtension() {
        assertThat(ImageType.PNG.contentType()).isEqualTo("image/png");
        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.WEBP.contentType()).isEqualTo("image/webp");
    }
}
