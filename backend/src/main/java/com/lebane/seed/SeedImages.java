package com.lebane.seed;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Fotos sintéticas para el seed: una ilustración simple (cielo, edificio con ventanas) generada en memoria con
 * Java2D, distinta según el departamento y el número de foto. No usa fuentes ni archivos externos, por lo que
 * funciona en un contenedor headless sin dependencias adicionales.
 */
final class SeedImages {

    private static final int WIDTH = 640;
    private static final int HEIGHT = 480;

    private SeedImages() {
    }

    static byte[] png(int departamento, int foto) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float hue = ((departamento * 37 + foto * 11) % 360) / 360f;
            g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue, 0.45f, 0.95f), 0, HEIGHT,
                    Color.getHSBColor(hue, 0.25f, 0.70f)));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setColor(new Color(70, 90, 60));
            g.fillRect(0, HEIGHT - 70, WIDTH, 70);

            int pisos = 4 + (departamento + foto) % 6;
            int ancho = 220 + (foto % 3) * 40;
            int x = (WIDTH - ancho) / 2;
            int alto = pisos * 42 + 20;
            int y = HEIGHT - 70 - alto;
            g.setColor(Color.getHSBColor((hue + 0.5f) % 1f, 0.15f, 0.55f + 0.1f * (foto % 3)));
            g.fillRect(x, y, ancho, alto);

            g.setColor(new Color(255, 236, 170));
            for (int piso = 0; piso < pisos; piso++) {
                for (int columna = 0; columna < ancho / 44; columna++) {
                    if ((piso * 7 + columna * 3 + departamento) % 5 != 0) {
                        g.fillRect(x + 14 + columna * 44, y + 16 + piso * 42, 24, 22);
                    }
                }
            }
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
