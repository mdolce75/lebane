import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { ImageWithFallback } from '../../../shared/components/ImageWithFallback';
import { departamentosKeys, useEliminarImagen } from '../api/queries';
import type { Imagen } from '../api/schemas';
import { ImagePicker } from './ImagePicker';
import { MAX_IMAGES } from './imageValidation';
import { uploadSequentially } from './uploadSequentially';
import { useImageSelection } from './useImageSelection';

type Props = { departamentoId: number; imagenes: Imagen[] };

/**
 * Fotos de un departamento existente: quitar las actuales y subir nuevas hasta completar 5. Cada operación
 * impacta de inmediato en el servidor (no depende de guardar el formulario).
 */
export function ImagenesManager({ departamentoId, imagenes }: Props) {
  const queryClient = useQueryClient();
  const eliminar = useEliminarImagen(departamentoId);
  const seleccion = useImageSelection(MAX_IMAGES - imagenes.length);
  const [subiendo, setSubiendo] = useState(false);
  const [eliminando, setEliminando] = useState<number | null>(null);

  const subir = async () => {
    setSubiendo(true);
    try {
      const fallidas = await uploadSequentially(departamentoId, seleccion.items, seleccion.setUpload);
      await queryClient.invalidateQueries({ queryKey: departamentosKeys.all });
      if (fallidas === 0) seleccion.removeUploaded();
    } finally {
      setSubiendo(false);
    }
  };

  const quitar = async (imagen: Imagen) => {
    if (!window.confirm('¿Eliminar esta foto? Esta acción no se puede deshacer.')) return;
    setEliminando(imagen.id);
    try {
      await eliminar.mutateAsync(imagen.id);
    } finally {
      setEliminando(null);
    }
  };

  const pendientes = seleccion.items.filter((i) => i.upload.status !== 'done').length;

  return (
    <section className="form__section form__section--full" aria-labelledby="fotos-titulo">
      <h2 id="fotos-titulo">Fotos ({imagenes.length} de {MAX_IMAGES})</h2>
      {imagenes.length > 0 ? (
        <ul className="thumbs" aria-label="Fotos actuales">
          {imagenes.map((imagen, index) => (
            <li key={imagen.id} className="thumb">
              <ImageWithFallback src={imagen.url} alt={`Foto ${index + 1}`} className="thumb__image" />
              <div className="thumb__info">
                <span>{index === 0 ? 'Principal' : `Foto ${index + 1}`}</span>
              </div>
              <button type="button" className="button button--ghost button--small" disabled={eliminando !== null}
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
      {pendientes > 0 && (
        <button type="button" className="button" onClick={subir} disabled={subiendo}>
          {subiendo ? 'Subiendo…' : `Subir ${pendientes} foto(s)`}
        </button>
      )}
    </section>
  );
}
