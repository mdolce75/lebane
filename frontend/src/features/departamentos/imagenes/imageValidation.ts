/**
 * Validación de fotos en el cliente, con las mismas reglas que el backend (que vuelve a validar): tipo por
 * contenido (magic bytes, no por extensión ni por el tipo que declara el navegador), tamaño máximo y cantidad.
 */
export const MAX_IMAGES = 5;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_TYPES = 'image/jpeg,image/png,image/webp';

export type ImageCheck = { ok: true } | { ok: false; reason: string };

const startsWith = (bytes: Uint8Array, offset: number, signature: number[]) =>
  bytes.length >= offset + signature.length && signature.every((b, i) => bytes[offset + i] === b);

export function detectImageType(bytes: Uint8Array): 'jpeg' | 'png' | 'webp' | null {
  if (startsWith(bytes, 0, [0xff, 0xd8, 0xff])) return 'jpeg';
  if (startsWith(bytes, 0, [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return 'png';
  if (startsWith(bytes, 0, [0x52, 0x49, 0x46, 0x46]) && startsWith(bytes, 8, [0x57, 0x45, 0x42, 0x50])) return 'webp';
  return null;
}

async function readHeader(file: Blob, length = 12): Promise<Uint8Array> {
  const slice = file.slice(0, length);
  if (typeof slice.arrayBuffer === 'function') {
    return new Uint8Array(await slice.arrayBuffer());
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(new Uint8Array(reader.result as ArrayBuffer));
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(slice);
  });
}

export async function checkImageFile(file: File): Promise<ImageCheck> {
  if (file.size === 0) return { ok: false, reason: 'El archivo está vacío' };
  if (file.size > MAX_IMAGE_BYTES) return { ok: false, reason: 'Supera el máximo de 5 MB' };
  try {
    const type = detectImageType(await readHeader(file));
    return type ? { ok: true } : { ok: false, reason: 'No es una imagen JPEG, PNG o WebP' };
  } catch {
    return { ok: false, reason: 'No se pudo leer el archivo' };
  }
}
