package com.lebane.storage.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formatos de imagen aceptados, identificados por su firma binaria (magic bytes) y no por el {@code Content-Type}
 * ni la extensión que declara el cliente, que se pueden falsificar.
 */
public enum ImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    /** Bytes necesarios para identificar cualquiera de los formatos. */
    public static final int SIGNATURE_LENGTH = 12;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARKER = {'W', 'E', 'B', 'P'};

    private final String contentType;
    private final String extension;

    ImageType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Detecta el formato a partir de los primeros bytes del archivo. */
    public static Optional<ImageType> detect(byte[] header) {
        if (startsWith(header, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(header, 0, RIFF) && startsWith(header, 8, WEBP_MARKER)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        return data != null && data.length >= offset + prefix.length
                && Arrays.equals(data, offset, offset + prefix.length, prefix, 0, prefix.length);
    }
}
