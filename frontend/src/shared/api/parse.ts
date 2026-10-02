import type { z } from 'zod';
import { HttpError } from './errors';

/**
 * Valida una respuesta del backend contra su esquema. Un desajuste de contrato se reporta como error de
 * respuesta inválida (mensaje seguro para el usuario), sin exponer el detalle de la validación.
 */
export function parseResponse<S extends z.ZodType>(schema: S, data: unknown): z.infer<S> {
  const result = schema.safeParse(data);
  if (!result.success) {
    if (import.meta.env.DEV) {
      console.error('Respuesta del backend con formato inesperado', result.error.issues);
    }
    throw new HttpError({ kind: 'parse', message: 'La respuesta del servidor no es válida.' });
  }
  return result.data;
}
