import { describe, expect, it } from 'vitest';
import { pngFile, textFile } from '../../../test/mockApi';
import { checkImageFile, detectImageType, MAX_IMAGE_BYTES } from './imageValidation';

describe('detectImageType', () => {
  it('detecta JPEG, PNG y WebP por su firma binaria', () => {
    expect(detectImageType(new Uint8Array([0xff, 0xd8, 0xff, 0xe0]))).toBe('jpeg');
    expect(detectImageType(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))).toBe('png');
    expect(detectImageType(new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50]))).toBe('webp');
    expect(detectImageType(new TextEncoder().encode('GIF89a......'))).toBeNull();
  });
});

describe('checkImageFile', () => {
  it('acepta una imagen válida', async () => {
    expect(await checkImageFile(pngFile())).toEqual({ ok: true });
  });

  it('rechaza un archivo que no es imagen aunque su nombre y tipo digan PNG', async () => {
    expect(await checkImageFile(textFile('foto.png'))).toEqual({ ok: false, reason: 'No es una imagen JPEG, PNG o WebP' });
  });

  it('rechaza archivos vacíos o de más de 5 MB', async () => {
    expect(await checkImageFile(new File([], 'vacia.png'))).toMatchObject({ ok: false, reason: 'El archivo está vacío' });
    expect(await checkImageFile(pngFile('grande.png', MAX_IMAGE_BYTES + 1)))
      .toMatchObject({ ok: false, reason: 'Supera el máximo de 5 MB' });
  });
});
