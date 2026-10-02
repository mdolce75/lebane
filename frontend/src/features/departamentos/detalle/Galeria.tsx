import { useState } from 'react';
import { ImageWithFallback } from '../../../shared/components/ImageWithFallback';
import type { Imagen } from '../api/schemas';

/** Foto principal grande y miniaturas para cambiarla. Cada imagen maneja su propio fallback si no carga. */
export function Galeria({ imagenes, titulo }: { imagenes: Imagen[]; titulo: string }) {
  const [seleccionada, setSeleccionada] = useState(0);
  const actual = imagenes[Math.min(seleccionada, imagenes.length - 1)];

  return (
    <div className="gallery">
      <ImageWithFallback
        src={actual?.url}
        alt={actual ? `${titulo}, foto ${seleccionada + 1} de ${imagenes.length}` : titulo}
        className="gallery__main"
        emptyLabel="Sin fotos"
      />
      {imagenes.length > 1 && (
        <ul className="gallery__thumbs" aria-label="Fotos">
          {imagenes.map((imagen, index) => (
            <li key={imagen.id}>
              <button
                type="button"
                className={`gallery__thumb ${index === seleccionada ? 'gallery__thumb--active' : ''}`}
                aria-label={`Ver foto ${index + 1}`}
                aria-pressed={index === seleccionada}
                onClick={() => setSeleccionada(index)}
              >
                <ImageWithFallback src={imagen.url} alt="" className="gallery__thumb-image" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
