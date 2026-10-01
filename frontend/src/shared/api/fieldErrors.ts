import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { isHttpError } from './errors';

/**
 * Lleva los `fieldErrors` de un 400 del backend a los campos del formulario (los nombres coinciden con las rutas
 * del request, p. ej. `direccion.ciudad`). Devuelve los que no corresponden a ningún campo conocido, para
 * mostrarlos en un mensaje general.
 */
export function applyServerFieldErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly string[],
): Record<string, string> {
  if (!isHttpError(error)) return {};
  const unmatched: Record<string, string> = {};
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (knownFields.includes(field)) {
      setError(field as Path<T>, { type: 'server', message });
    } else {
      unmatched[field] = message;
    }
  }
  return unmatched;
}
