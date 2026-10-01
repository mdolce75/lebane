import { useCallback, useEffect, useRef, useState } from 'react';
import { checkImageFile } from './imageValidation';

export type UploadState =
  | { status: 'pending' }
  | { status: 'uploading' }
  | { status: 'done' }
  | { status: 'error'; message: string; requestId: string | null };

export type SelectedImage = {
  id: string;
  file: File;
  previewUrl: string;
  upload: UploadState;
};

export type RejectedFile = { name: string; reason: string };

let sequence = 0;

/**
 * Fotos elegidas en el navegador antes de subirlas: valida cada archivo, respeta el cupo disponible
 * (`maxFiles`), genera vistas previas y libera sus object URLs al quitarlas o al desmontar (sin fugas de memoria).
 */
export function useImageSelection(maxFiles: number) {
  const [items, setItems] = useState<SelectedImage[]>([]);
  const [rejected, setRejected] = useState<RejectedFile[]>([]);
  const itemsRef = useRef(items);
  itemsRef.current = items;

  useEffect(() => () => itemsRef.current.forEach((item) => URL.revokeObjectURL(item.previewUrl)), []);

  const add = useCallback(
    async (files: FileList | File[]) => {
      const nuevos: SelectedImage[] = [];
      const rechazados: RejectedFile[] = [];
      let disponibles = maxFiles - itemsRef.current.length;
      for (const file of Array.from(files)) {
        if (disponibles <= 0) {
          rechazados.push({ name: file.name, reason: `Se alcanzó el máximo de fotos (${maxFiles} disponibles)` });
          continue;
        }
        const check = await checkImageFile(file);
        if (!check.ok) {
          rechazados.push({ name: file.name, reason: check.reason });
          continue;
        }
        nuevos.push({ id: `img-${++sequence}`, file, previewUrl: URL.createObjectURL(file), upload: { status: 'pending' } });
        disponibles--;
      }
      setItems((current) => [...current, ...nuevos]);
      setRejected(rechazados);
    },
    [maxFiles],
  );

  const remove = useCallback((id: string) => {
    setItems((current) => {
      const item = current.find((i) => i.id === id);
      if (item) URL.revokeObjectURL(item.previewUrl);
      return current.filter((i) => i.id !== id);
    });
  }, []);

  const setUpload = useCallback((id: string, upload: UploadState) => {
    setItems((current) => current.map((i) => (i.id === id ? { ...i, upload } : i)));
  }, []);

  /** Quita las ya subidas (sus vistas previas ya no hacen falta). */
  const removeUploaded = useCallback(() => {
    setItems((current) => {
      current.filter((i) => i.upload.status === 'done').forEach((i) => URL.revokeObjectURL(i.previewUrl));
      return current.filter((i) => i.upload.status !== 'done');
    });
  }, []);

  return { items, rejected, add, remove, setUpload, removeUploaded, remaining: maxFiles - items.length };
}
