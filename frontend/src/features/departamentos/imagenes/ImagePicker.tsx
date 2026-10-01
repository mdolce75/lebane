import { useId } from 'react';
import { formatBytes } from '../../../shared/format/format';
import { ACCEPTED_TYPES } from './imageValidation';
import type { RejectedFile, SelectedImage } from './useImageSelection';

type Props = {
  items: SelectedImage[];
  rejected: RejectedFile[];
  remaining: number;
  total: number;
  onAdd: (files: FileList) => void;
  onRemove: (id: string) => void;
  disabled?: boolean;
};

const STATUS_LABEL = { pending: 'Pendiente', uploading: 'Subiendo…', done: 'Subida', error: 'Error' } as const;

/**
 * Selección de fotos con vista previa, quitar antes de enviar y estado de subida por foto.
 */
export function ImagePicker({ items, rejected, remaining, total, onAdd, onRemove, disabled = false }: Props) {
  const inputId = useId();

  return (
    <div className="image-picker">
      <div className="image-picker__header">
        <label htmlFor={inputId} className={`button button--ghost ${disabled || remaining <= 0 ? 'button--disabled' : ''}`}>
          Agregar fotos
        </label>
        <input
          id={inputId}
          type="file"
          accept={ACCEPTED_TYPES}
          multiple
          className="visually-hidden"
          disabled={disabled || remaining <= 0}
          aria-label="Agregar fotos"
          onChange={(e) => {
            if (e.target.files?.length) onAdd(e.target.files);
            e.target.value = ''; // permite volver a elegir el mismo archivo
          }}
        />
        <span className="muted">
          JPEG, PNG o WebP, hasta 5 MB. {remaining > 0 ? `Podés agregar ${remaining} más` : 'Máximo alcanzado'} (de {total}).
        </span>
      </div>

      {rejected.length > 0 && (
        <ul className="image-picker__rejected" role="alert" aria-label="Archivos rechazados">
          {rejected.map((r, index) => (
            <li key={`${r.name}-${index}`}>
              <strong>{r.name}</strong>: {r.reason}
            </li>
          ))}
        </ul>
      )}

      {items.length > 0 && (
        <ul className="thumbs" aria-label="Fotos seleccionadas">
          {items.map((item) => (
            <li key={item.id} className={`thumb thumb--${item.upload.status}`}>
              <img src={item.previewUrl} alt={`Vista previa de ${item.file.name}`} className="thumb__image" />
              <div className="thumb__info">
                <span className="thumb__name" title={item.file.name}>{item.file.name}</span>
                <span className="muted">{formatBytes(item.file.size)} · {STATUS_LABEL[item.upload.status]}</span>
                {item.upload.status === 'error' && (
                  <span className="field__error">
                    {item.upload.message}
                    {item.upload.requestId && <> (código {item.upload.requestId})</>}
                  </span>
                )}
              </div>
              {item.upload.status !== 'uploading' && item.upload.status !== 'done' && (
                <button type="button" className="button button--ghost button--small" disabled={disabled}
                  onClick={() => onRemove(item.id)} aria-label={`Quitar ${item.file.name}`}>
                  Quitar
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
