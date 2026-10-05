package com.lebane.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Códigos de error estables expuestos en {@link ApiError#error()} y en el campo de log {@code errorCode}.
 * Los clientes pueden depender de ellos; los mensajes, en cambio, son solo informativos.
 */
@Schema(description = "Código estable del error. Los clientes pueden depender de él; el mensaje es solo informativo.")
public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    NOT_ACCEPTABLE,
    CONFLICT,
    /** El recurso fue modificado por otra operación (concurrencia optimista). */
    CONCURRENT_MODIFICATION,
    /** {@code If-Match} no coincide con la versión actual. */
    PRECONDITION_FAILED,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    /** Regla de negocio: el departamento no admite la operación en su estado actual (p. ej. vendido). */
    DEPARTAMENTO_NO_DISPONIBLE,
    /** Regla de negocio: el cambio de estado no está permitido (p. ej. salir de VENDIDO, o publicar como vendido). */
    TRANSICION_DE_ESTADO_INVALIDA,
    /** Regla de negocio: el departamento está dado de baja; para modificarlo hay que reactivarlo. */
    DEPARTAMENTO_DADO_DE_BAJA,
    /** Regla de negocio: se pidió reactivar un departamento que no está dado de baja. */
    DEPARTAMENTO_NO_DADO_DE_BAJA,
    /** Regla de negocio: ya hay un departamento publicado (no vendido) en la misma dirección. */
    AVISO_DUPLICADO,
    /** Regla de negocio: el mismo email ya consultó por el departamento en las últimas 24 horas. */
    CONSULTA_DUPLICADA,
    /** Regla de negocio: el departamento ya tiene el máximo de fotos. */
    LIMITE_IMAGENES_ALCANZADO,
    /** El storage de imágenes no está disponible (caído, timeout, circuito abierto o mal configurado). */
    STORAGE_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
    INTERNAL_ERROR
}
