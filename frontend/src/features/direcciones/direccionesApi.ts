import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { http } from '../../shared/api/httpClient';
import { parseResponse } from '../../shared/api/parse';

const sugerenciaSchema = z.object({
  calle: z.string(),
  numero: z.string().nullable(),
  ciudad: z.string().nullable(),
  provincia: z.string().nullable(),
  latitud: z.coerce.number().nullable(),
  longitud: z.coerce.number().nullable(),
  placeId: z.string().nullable(),
  descripcion: z.string().nullable(),
});

const autocompleteSchema = z.object({
  sugerencias: z.array(sugerenciaSchema),
  proveedor: z.string(),
  degradado: z.boolean(),
  mensaje: z.string().optional(),
});

export type SugerenciaDireccion = z.infer<typeof sugerenciaSchema>;
export type AutocompleteResult = z.infer<typeof autocompleteSchema>;

export const MIN_QUERY = 3;

export async function autocompletarDireccion(q: string, signal?: AbortSignal): Promise<AutocompleteResult> {
  const data = await http.get<unknown>('/v1/direcciones/autocompletar', { query: { q, limite: 5 }, signal });
  return parseResponse(autocompleteSchema, data);
}

/** Sugerencias para un texto ya "debounced". No reintenta: el backend ya aplica su propia resiliencia. */
export function useAutocompletarDireccion(q: string) {
  const texto = q.trim();
  return useQuery({
    queryKey: ['direcciones', 'autocompletar', texto],
    queryFn: ({ signal }) => autocompletarDireccion(texto, signal),
    enabled: texto.length >= MIN_QUERY,
    staleTime: 5 * 60_000,
    retry: false,
  });
}
