import { pageItems } from './pageItems';

type Props = {
  /** Página actual, desde 0. */
  page: number;
  totalPages: number;
  /** Última página navegable (ventana máxima del backend); por defecto, la última. */
  lastNavigablePage?: number;
  onChange: (page: number) => void;
  disabled?: boolean;
};

/** Paginación del servidor: cada cambio de página es un request nuevo; nunca se pagina en el cliente. */
export function Pagination({ page, totalPages, lastNavigablePage, onChange, disabled = false }: Props) {
  if (totalPages <= 1) return null;
  const last = Math.min(totalPages - 1, lastNavigablePage ?? totalPages - 1);

  return (
    <nav className="pagination" aria-label="Paginación">
      <button type="button" className="button button--ghost" disabled={disabled || page <= 0}
        onClick={() => onChange(page - 1)}>
        Anterior
      </button>
      <ul className="pagination__pages">
        {pageItems(page, last).map((item, index) =>
          item === 'gap' ? (
            <li key={`gap-${index}`} aria-hidden="true" className="pagination__gap">…</li>
          ) : (
            <li key={item}>
              <button
                type="button"
                className={`pagination__page ${item === page ? 'pagination__page--current' : ''}`}
                aria-current={item === page ? 'page' : undefined}
                aria-label={`Página ${item + 1}`}
                disabled={disabled}
                onClick={() => onChange(item)}
              >
                {item + 1}
              </button>
            </li>
          ),
        )}
      </ul>
      <button type="button" className="button button--ghost" disabled={disabled || page >= last}
        onClick={() => onChange(page + 1)}>
        Siguiente
      </button>
    </nav>
  );
}
