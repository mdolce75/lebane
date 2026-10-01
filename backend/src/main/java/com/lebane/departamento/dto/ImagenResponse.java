package com.lebane.departamento.dto;

/** Foto de un departamento. {@code url} la resuelve el storage; la clave interna del objeto no se expone. */
public record ImagenResponse(Long id, String url, String contentType, long sizeBytes, int posicion) {
}
