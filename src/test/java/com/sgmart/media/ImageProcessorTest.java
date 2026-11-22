package com.sgmart.media;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {

    private static byte[] image(String format, int w, int h, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillOval(10, 10, w - 20, h - 20);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void transparentPngBecomesWhiteBackedJpegWithin600() throws Exception {
        ImageProcessor.ProcessedImage result = ImageProcessor.process(image("png", 1200, 800, true));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(out.getWidth()).isEqualTo(600);
        assertThat(out.getHeight()).isEqualTo(400);
        assertThat(out.getRGB(1, 1) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void smallPhotosAreNotEnlarged() throws Exception {
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(
                ImageProcessor.process(image("jpg", 200, 150, false)).bytes()));
        assertThat(out.getWidth()).isEqualTo(200);
    }

    @Test
    void nonImagesEmptyAndHugeFilesAreRefused() {
        assertThatThrownBy(() -> ImageProcessor.process("hello".getBytes())).isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> ImageProcessor.process(new byte[0])).isInstanceOf(InvalidImageException.class);
        assertThatThrownBy(() -> ImageProcessor.process(new byte[6 * 1024 * 1024])).isInstanceOf(InvalidImageException.class);
    }

    @Test
    void storageKeysBlockPathTricks() {
        assertThat(ImageStorage.isValidKey("3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90.jpg")).isTrue();
        assertThat(ImageStorage.isValidKey("../../etc/passwd")).isFalse();
    }
}
