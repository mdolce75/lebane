import { isHttpError } from '../../../shared/api/errors';
import { subirImagen } from '../api/departamentosApi';
import type { SelectedImage, UploadState } from './useImageSelection';

/**
 * Sube las fotos de a una, en orden (una por request, como espera el backend). Cada foto informa su propio
 * resultado: un error no detiene las demás (errores parciales) y las fallidas se pueden reintentar.
 *
 * @returns cantidad de fotos con error
 */
export async function uploadSequentially(
  departamentoId: number,
  items: SelectedImage[],
  setUpload: (id: string, state: UploadState) => void,
): Promise<number> {
  let failures = 0;
  for (const item of items) {
    if (item.upload.status === 'done') continue;
    setUpload(item.id, { status: 'uploading' });
    try {
      await subirImagen(departamentoId, item.file);
      setUpload(item.id, { status: 'done' });
    } catch (error) {
      failures++;
      setUpload(item.id, {
        status: 'error',
        message: isHttpError(error) ? uploadErrorMessage(error.code, error.message) : 'No se pudo subir la foto.',
        requestId: isHttpError(error) ? error.requestId : null,
      });
    }
  }
  return failures;
}

function uploadErrorMessage(code: string, message: string): string {
  if (code === 'STORAGE_UNAVAILABLE') return 'El servicio de imágenes no está disponible. Reintentá en unos minutos.';
  if (code === 'LIMITE_IMAGENES_ALCANZADO') return 'El departamento ya tiene el máximo de 5 fotos.';
  return message;
}
