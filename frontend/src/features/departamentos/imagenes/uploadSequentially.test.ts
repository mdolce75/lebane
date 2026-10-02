import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpError } from '../../../shared/api/errors';
import { subirImagen } from '../api/departamentosApi';
import { uploadSequentially } from './uploadSequentially';
import type { SelectedImage, UploadState } from './useImageSelection';

vi.mock('../api/departamentosApi', () => ({ subirImagen: vi.fn() }));

const subir = vi.mocked(subirImagen);

function item(id: string, upload: UploadState = { status: 'pending' }): SelectedImage {
  return { id, file: new File(['x'], `${id}.png`, { type: 'image/png' }), previewUrl: `blob:${id}`, upload };
}

describe('uploadSequentially', () => {
  beforeEach(() => {
    subir.mockReset();
  });

  it('sube de a una, en orden, y saltea las ya subidas', async () => {
    const orden: string[] = [];
    let enCurso = 0;
    subir.mockImplementation(async (_id, file) => {
      enCurso++;
      expect(enCurso).toBe(1); // nunca dos requests a la vez
      orden.push(file.name);
      await Promise.resolve();
      enCurso--;
      return { id: 1, url: 'u', contentType: 'image/png', sizeBytes: 1, posicion: 0 };
    });
    const setUpload = vi.fn();

    const fallas = await uploadSequentially(7, [item('a'), item('b', { status: 'done' }), item('c')], setUpload);

    expect(fallas).toBe(0);
    expect(orden).toEqual(['a.png', 'c.png']);
    expect(subir).toHaveBeenCalledWith(7, expect.any(File));
    expect(setUpload.mock.calls).toEqual([
      ['a', { status: 'uploading' }],
      ['a', { status: 'done' }],
      ['c', { status: 'uploading' }],
      ['c', { status: 'done' }],
    ]);
  });

  it('un error no detiene las demás y cada foto informa su mensaje y requestId', async () => {
    subir
      .mockRejectedValueOnce(new HttpError({ kind: 'http', status: 409, code: 'LIMITE_IMAGENES_ALCANZADO', message: 'x', requestId: 'req-1' }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockRejectedValueOnce(new HttpError({ kind: 'http', status: 415, code: 'UNSUPPORTED_MEDIA_TYPE', message: 'Tipo no admitido', requestId: 'req-3' }))
      .mockResolvedValueOnce({ id: 4, url: 'u', contentType: 'image/png', sizeBytes: 1, posicion: 0 });
    const setUpload = vi.fn();

    const fallas = await uploadSequentially(7, [item('a'), item('b'), item('c'), item('d')], setUpload);

    expect(fallas).toBe(3);
    expect(setUpload).toHaveBeenCalledWith('a', { status: 'error', message: 'El departamento ya tiene el máximo de 5 fotos.', requestId: 'req-1' });
    expect(setUpload).toHaveBeenCalledWith('b', { status: 'error', message: 'No se pudo subir la foto.', requestId: null });
    expect(setUpload).toHaveBeenCalledWith('c', { status: 'error', message: 'Tipo no admitido', requestId: 'req-3' });
    expect(setUpload).toHaveBeenLastCalledWith('d', { status: 'done' });
  });
});
