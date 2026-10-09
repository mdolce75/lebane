package com.lebane.departamento.service;

/** Foto ya subida al storage, pendiente de registrarse junto con el alta del departamento. */
record ImagenSubida(String objectKey, String contentType, long sizeBytes) {
}
