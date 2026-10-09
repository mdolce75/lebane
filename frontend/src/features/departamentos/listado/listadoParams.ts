import { z } from 'zod';
import { camposOrden, estadoSchema, monedaSchema, type Estado, type Moneda } from '../api/schemas';

/**
 * Estado del listado (filtros, orden y página), con la URL como fuente de verdad: los resultados se pueden
 * compartir, recargar y navegar con "atrás". Los valores inválidos de la URL se descartan (se usan los defaults)
 * en lugar de provocar un 400.
 */
export const PAGE_SIZES = [12, 24, 48] as const;
export const DEFAULT_SIZE = 12;
/** Misma ventana que el backend: (page + 1) * size <= 10.000. */
export const MAX_RESULT_WINDOW = 10_000;

export type ListadoParams = {
  q?: string;
  ciudad?: string;
  estado: Estado[];
  moneda?: Moneda;
  precioMin?: number;
  precioMax?: number;
  superficieMin?: number;
  superficieMax?: number;
  ambientesMin?: number;
  conImagenes?: boolean;
  /** true: solo los dados de baja (para reactivarlos). Sin el parámetro, solo los publicados. */
  dadosDeBaja?: true;
  page: number;
  size: number;
  sort: string;
};

export const SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'createdAt,desc', label: 'Más recientes' },
  { value: 'createdAt,asc', label: 'Más antiguos' },
  { value: 'precio,asc', label: 'Menor precio' },
  { value: 'precio,desc', label: 'Mayor precio' },
  { value: 'superficieM2,desc', label: 'Mayor superficie' },
  { value: 'superficieM2,asc', label: 'Menor superficie' },
];
export const DEFAULT_SORT = 'createdAt,desc';

const sortPattern = new RegExp(`^(${camposOrden.join('|')}),(asc|desc)$`);

const optionalText = (min: number, max: number) =>
  z.string().trim().min(min).max(max).optional().catch(undefined);
const optionalNumber = (min: number) => z.coerce.number().int().min(min).optional().catch(undefined);
const optionalAmount = z.coerce.number().nonnegative().optional().catch(undefined);

const urlSchema = z.object({
  q: optionalText(3, 100),
  ciudad: optionalText(1, 80),
  moneda: monedaSchema.optional().catch(undefined),
  precioMin: optionalAmount,
  precioMax: optionalAmount,
  superficieMin: optionalAmount,
  superficieMax: optionalAmount,
  ambientesMin: optionalNumber(1),
  conImagenes: z.enum(['true', 'false']).transform((v) => v === 'true').optional().catch(undefined),
  dadosDeBaja: z.literal('true').transform(() => true as const).optional().catch(undefined),
  page: z.coerce.number().int().min(0).catch(0),
  size: z.coerce
    .number()
    .refine((n) => (PAGE_SIZES as readonly number[]).includes(n))
    .catch(DEFAULT_SIZE),
  sort: z.string().regex(sortPattern).catch(DEFAULT_SORT),
});

export function parseListadoParams(search: URLSearchParams): ListadoParams {
  const raw = Object.fromEntries([...search.entries()].filter(([key]) => key !== 'estado'));
  const parsed = urlSchema.parse(raw);
  const estado = search
    .getAll('estado')
    .map((value) => estadoSchema.safeParse(value))
    .flatMap((result) => (result.success ? [result.data] : []));
  // Con los dados de baja, el estado no aplica (una URL armada a mano con los dos no los combina).
  const params: ListadoParams = { ...parsed, estado: parsed.dadosDeBaja ? [] : [...new Set(estado)] };
  // El precio solo se filtra con moneda (ARS y USD no son comparables): sin moneda, se ignora.
  if (!params.moneda) {
    params.precioMin = undefined;
    params.precioMax = undefined;
  }
  if (params.precioMin !== undefined && params.precioMax !== undefined && params.precioMin > params.precioMax) {
    params.precioMax = undefined;
  }
  if (
    params.superficieMin !== undefined && params.superficieMax !== undefined &&
    params.superficieMin > params.superficieMax
  ) {
    params.superficieMax = undefined;
  }
  params.page = Math.min(params.page, maxPage(params.size));
  return params;
}

export function toSearchParams(params: Partial<ListadoParams>): URLSearchParams {
  const search = new URLSearchParams();
  const set = (key: string, value: unknown) => {
    if (value === undefined || value === null || value === '') return;
    search.set(key, String(value));
  };
  set('q', params.q);
  set('ciudad', params.ciudad);
  params.estado?.forEach((estado) => search.append('estado', estado));
  set('moneda', params.moneda);
  set('precioMin', params.precioMin);
  set('precioMax', params.precioMax);
  set('superficieMin', params.superficieMin);
  set('superficieMax', params.superficieMax);
  set('ambientesMin', params.ambientesMin);
  set('conImagenes', params.conImagenes);
  set('dadosDeBaja', params.dadosDeBaja);
  if (params.page) set('page', params.page);
  if (params.size && params.size !== DEFAULT_SIZE) set('size', params.size);
  if (params.sort && params.sort !== DEFAULT_SORT) set('sort', params.sort);
  return search;
}

/** Última página navegable según la ventana máxima de resultados del backend. */
export function maxPage(size: number): number {
  return Math.floor(MAX_RESULT_WINDOW / size) - 1;
}

export function hasActiveFilters(params: ListadoParams): boolean {
  return Boolean(
    params.q || params.ciudad || params.estado.length || params.moneda || params.ambientesMin ||
      params.superficieMin !== undefined || params.superficieMax !== undefined ||
      params.conImagenes !== undefined || params.dadosDeBaja,
  );
}
