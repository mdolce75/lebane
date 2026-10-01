package com.lebane.storage;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/** Contenido de imágenes para tests: firmas binarias y PNG reales generados en memoria. */
public final class TestImages {

    public static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    public static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J',
            'F', 'I', 'F', 0, 1};
    public static final byte[] WEBP_HEADER = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'};

    private TestImages() {
    }

    /** Bytes con firma PNG seguida de relleno (suficiente para la detección por contenido). */
    public static byte[] pngLike(int size) {
        byte[] content = new byte[size];
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);
        return content;
    }

    /** PNG válido y decodificable de 32x32. */
    public static byte[] realPng() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().setColor(Color.ORANGE);
        image.getGraphics().fillRect(0, 0, 32, 32);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
