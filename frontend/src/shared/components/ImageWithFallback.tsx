import { useState } from 'react';

type Props = {
  src: string | null | undefined;
  alt: string;
  className?: string;
  /** Texto del placeholder cuando no hay imagen (distinto del de imagen rota). */
  emptyLabel?: string;
};

/**
 * Imagen con placeholder: si no hay URL muestra "Sin fotos"; si la URL falla al cargar (objeto borrado, storage
 * caído, archivo corrupto) muestra "Imagen no disponible" en lugar del ícono roto del navegador.
 */
export function ImageWithFallback({ src, alt, className, emptyLabel = 'Sin fotos' }: Props) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);

  if (!src || failedSrc === src) {
    const label = src ? 'Imagen no disponible' : emptyLabel;
    return (
      <div className={`image-placeholder ${className ?? ''}`} role="img" aria-label={`${alt}: ${label}`}>
        <svg viewBox="0 0 24 24" aria-hidden="true" className="image-placeholder__icon">
          <path d="M3 5h18v14H3z M7 15l3-4 3 3 2-2 3 3" fill="none" stroke="currentColor" strokeWidth="1.5" />
        </svg>
        <span>{label}</span>
      </div>
    );
  }
  return (
    <img
      src={src}
      alt={alt}
      className={className}
      loading="lazy"
      decoding="async"
      onError={() => setFailedSrc(src)}
    />
  );
}
