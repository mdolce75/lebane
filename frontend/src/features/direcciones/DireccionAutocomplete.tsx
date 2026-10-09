import { useId, useState } from 'react';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import { MIN_QUERY, useAutocompletarDireccion, type SugerenciaDireccion } from './direccionesApi';

type Props = {
  onSelect: (sugerencia: SugerenciaDireccion) => void;
};

/**
 * Búsqueda de direcciones: elegir una sugerencia completa la dirección del formulario. Si el proveedor no está
 * disponible (`degradado`) o no hay sugerencias, el formulario permite cargarla a mano.
 */
export function DireccionAutocomplete({ onSelect }: Props) {
  const [texto, setTexto] = useState('');
  const [abierto, setAbierto] = useState(false);
  const debounced = useDebouncedValue(texto, 300);
  const { data, isFetching, isError } = useAutocompletarDireccion(debounced);
  const listId = useId();
  const inputId = useId();

  const sugerencias = data?.sugerencias ?? [];
  const mostrarResultados = abierto && debounced.trim().length >= MIN_QUERY;

  const elegir = (sugerencia: SugerenciaDireccion) => {
    onSelect(sugerencia);
    setTexto(sugerencia.descripcion ?? `${sugerencia.calle} ${sugerencia.numero ?? ''}`.trim());
    setAbierto(false);
  };

  return (
    <div className="autocomplete">
      <label htmlFor={inputId}>Buscar dirección</label>
      <input
        id={inputId}
        type="search"
        autoComplete="off"
        placeholder="Ej.: Av. Santa Fe 1860"
        value={texto}
        aria-controls={listId}
        aria-expanded={mostrarResultados}
        onChange={(e) => {
          setTexto(e.target.value);
          setAbierto(true);
        }}
        onKeyDown={(e) => {
          if (e.key === 'Escape') setAbierto(false);
        }}
      />
      <span className="field__hint">Escribí calle y altura, y elegí una de las sugerencias.</span>
      {mostrarResultados && (
        <div id={listId} className="autocomplete__results" aria-live="polite">
          {isFetching && <p className="muted">Buscando…</p>}
          {!isFetching && isError && (
            <p className="muted">No se pudo consultar el autocompletado. Ingresá la dirección manualmente.</p>
          )}
          {!isFetching && data?.degradado && <p className="muted">{data.mensaje}</p>}
          {!isFetching && data && !data.degradado && sugerencias.length === 0 && (
            <p className="muted">Sin sugerencias para “{debounced}”.</p>
          )}
          {sugerencias.length > 0 && (
            <ul className="autocomplete__list" aria-label="Sugerencias de direcciones">
              {sugerencias.map((s, index) => (
                <li key={s.placeId ?? index}>
                  <button type="button" className="autocomplete__option" onClick={() => elegir(s)}>
                    {s.descripcion ?? `${s.calle} ${s.numero ?? ''}, ${s.ciudad ?? ''}`}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
