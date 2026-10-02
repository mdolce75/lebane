import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router';
import { parseListadoParams, toSearchParams, type ListadoParams } from './listadoParams';

/**
 * Estado del listado sincronizado con la URL.
 *
 * Cada cambio se compone sobre el último estado pedido, no sobre el del último render: si el usuario aplica un
 * filtro y enseguida cambia el orden (antes de que el router confirme la primera navegación), el segundo cambio
 * conserva el filtro en lugar de pisarlo.
 */
export function useListadoSearch() {
  const [search, setSearch] = useSearchParams();
  const params = useMemo(() => parseListadoParams(search), [search]);
  const pendiente = useRef<ListadoParams | null>(null);

  // Cuando la URL refleja el último cambio (o cambia desde afuera: atrás/adelante), no hay nada pendiente.
  useEffect(() => {
    pendiente.current = null;
  }, [search]);

  const update = useCallback(
    (changes: Partial<ListadoParams>, resetPage = true) => {
      const next = { ...(pendiente.current ?? params), ...changes, ...(resetPage ? { page: 0 } : {}) };
      pendiente.current = next;
      setSearch(toSearchParams(next));
    },
    [params, setSearch],
  );

  const clear = useCallback(() => {
    pendiente.current = parseListadoParams(new URLSearchParams());
    setSearch(new URLSearchParams());
  }, [setSearch]);

  return { params, update, clear };
}
