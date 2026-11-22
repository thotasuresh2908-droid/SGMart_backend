package com.sgmart.media;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Checks and shrinks product photos. Plain JDK only (no extra libraries).
 *  - JPG and PNG: resized to fit 600 x 600 and saved as JPEG. Re-encoding also strips
 *    camera data such as GPS location.
 *  - WebP: Java can't decode it without a plugin, so it is checked and stored as-is (max 2 MB).
 *    The web app already resizes photos before upload, so this is rare.
 */
public final class ImageProcessor {

    public static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;
    public static final long MAX_WEBP_BYTES = 2L * 1024 * 1024;
    public static final int MAX_SIDE = 600;

    public record ProcessedImage(byte[] bytes, String extension, String contentType) {
    }

    private ImageProcessor() {
    }

    public static ProcessedImage process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidImageException("Choose a photo to upload.");
        }
        if (input.length > MAX_UPLOAD_BYTES) {
            throw new InvalidImageException("The photo is larger than 5 MB. Choose a smaller one.");
        }
        if (isWebp(input)) {
            if (input.length > MAX_WEBP_BYTES) {
                throw new InvalidImageException("WebP photos must be under 2 MB. Choose a smaller one.");
            }
            return new ProcessedImage(input, "webp", "image/webp");
        }

        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(input));
        } catch (IOException e) {
            source = null;
        }
        if (source == null) {
            throw new InvalidImageException("This file isn't a JPG, PNG or WebP photo.");
        }
        return new ProcessedImage(toJpeg(resize(source)), "jpg", "image/jpeg");
    }

    static boolean isWebp(byte[] b) {
        return b.length > 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    /** Fits inside MAX_SIDE x MAX_SIDE and paints transparent areas white (JPEG has no transparency). */
    private static BufferedImage resize(BufferedImage source) {
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the photo", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
