import { useState } from 'react';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { ImageWithFallback } from '../../../shared/components/ImageWithFallback';
import { useEliminarImagen } from '../api/queries';
import type { Imagen } from '../api/schemas';
import { ImagePicker } from './ImagePicker';
import { MAX_IMAGES } from './imageValidation';
import type { useImageSelection } from './useImageSelection';

type Props = {
  departamentoId: number;
  imagenes: Imagen[];
  /** Fotos nuevas elegidas: se suben al guardar el formulario, como en el alta. */
  seleccion: ReturnType<typeof useImageSelection>;
  subiendo: boolean;
};

/**
 * Fotos de un departamento existente, dentro del formulario de edición (antes de Guardar y Cancelar). Quitar una
 * foto actual impacta de inmediato (pide confirmación); las nuevas se suben al guardar.
 */
export function ImagenesManager({ departamentoId, imagenes, seleccion, subiendo }: Props) {
  const eliminar = useEliminarImagen(departamentoId);
  const [eliminando, setEliminando] = useState<number | null>(null);

  const quitar = async (imagen: Imagen) => {
    if (!window.confirm('¿Eliminar esta foto? Esta acción no se puede deshacer.')) return;
    setEliminando(imagen.id);
    try {
      await eliminar.mutateAsync(imagen.id);
    } finally {
      setEliminando(null);
    }
  };

  return (
    <fieldset className="form__section form__section--full">
      <legend>Fotos ({imagenes.length} de {MAX_IMAGES})</legend>
      {imagenes.length > 0 ? (
        <ul className="thumbs" aria-label="Fotos actuales">
          {imagenes.map((imagen, index) => (
            <li key={imagen.id} className="thumb">
              <ImageWithFallback src={imagen.url} alt={`Foto ${index + 1}`} className="thumb__image" />
              <div className="thumb__info">
                <span>{index === 0 ? 'Principal' : `Foto ${index + 1}`}</span>
              </div>
              <button type="button" className="button button--ghost button--small" disabled={eliminando !== null || subiendo}
                onClick={() => quitar(imagen)} aria-label={`Eliminar foto ${index + 1}`}>
                {eliminando === imagen.id ? 'Eliminando…' : 'Eliminar'}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted">Este departamento todavía no tiene fotos.</p>
      )}
      {eliminar.isError && <ErrorMessage error={eliminar.error} title="No se pudo eliminar la foto" />}

      <ImagePicker
        items={seleccion.items}
        rejected={seleccion.rejected}
        remaining={seleccion.remaining}
        total={MAX_IMAGES}
        onAdd={(files) => void seleccion.add(files)}
        onRemove={seleccion.remove}
        disabled={subiendo}
      />
      {subiendo && <p className="muted" role="status">Subiendo fotos…</p>}
    </fieldset>
  );
}
