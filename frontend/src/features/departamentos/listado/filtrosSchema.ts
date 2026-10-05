import { z } from 'zod';
import { estados, monedas } from '../api/schemas';
import type { ListadoParams } from './listadoParams';

/** Texto opcional de un input: vacío = sin filtro. */
const texto = z.string().trim();
/** Número opcional ingresado como texto: vacío = sin filtro. */
const numeroOpcional = z
  .string()
  .trim()
  .refine((v) => v === '' || (/^\d+([.,]\d{1,2})?$/.test(v) && Number(v.replace(',', '.')) >= 0), {
    message: 'Ingresá un número válido',
  });

/** Formulario de filtros: mismas reglas que el backend, para no enviar requests que van a devolver 400. */
export const filtrosSchema = z
  .object({
    q: texto.refine((v) => v === '' || (v.length >= 3 && v.length <= 100), {
      message: 'Ingresá al menos 3 caracteres',
    }),
    ciudad: texto.max(80, 'Máximo 80 caracteres'),
    estado: z.array(z.enum(estados)),
    moneda: z.union([z.enum(monedas), z.literal('')]),
    precioMin: numeroOpcional,
    precioMax: numeroOpcional,
    ambientesMin: z.union([z.literal(''), z.string().regex(/^([1-9]|1\d|20)$/, 'Entre 1 y 20')]),
    conImagenes: z.enum(['', 'true', 'false']),
    dadosDeBaja: z.boolean(),
  })
  .superRefine((value, ctx) => {
    if ((value.precioMin || value.precioMax) && !value.moneda) {
      ctx.addIssue({ code: 'custom', path: ['moneda'], message: 'Elegí la moneda para filtrar por precio' });
    }
    const min = toNumber(value.precioMin);
    const max = toNumber(value.precioMax);
    if (min !== undefined && max !== undefined && min > max) {
      ctx.addIssue({ code: 'custom', path: ['precioMax'], message: 'Debe ser mayor o igual al mínimo' });
    }
  });

export type FiltrosForm = z.infer<typeof filtrosSchema>;

function toNumber(value: string): number | undefined {
  return value === '' ? undefined : Number(value.replace(',', '.'));
}

export function filtrosFromParams(params: ListadoParams): FiltrosForm {
  return {
    q: params.q ?? '',
    ciudad: params.ciudad ?? '',
    estado: params.estado,
    moneda: params.moneda ?? '',
    precioMin: params.precioMin?.toString() ?? '',
    precioMax: params.precioMax?.toString() ?? '',
    ambientesMin: params.ambientesMin?.toString() ?? '',
    conImagenes: params.conImagenes === undefined ? '' : String(params.conImagenes) as 'true' | 'false',
    dadosDeBaja: params.dadosDeBaja === true,
  };
}

export function paramsFromFiltros(form: FiltrosForm): Partial<ListadoParams> {
  return {
    q: form.q || undefined,
    ciudad: form.ciudad || undefined,
    // Con los dados de baja, el estado no aplica.
    estado: form.dadosDeBaja ? [] : form.estado,
    moneda: form.moneda || undefined,
    precioMin: toNumber(form.precioMin),
    precioMax: toNumber(form.precioMax),
    ambientesMin: form.ambientesMin ? Number(form.ambientesMin) : undefined,
    conImagenes: form.conImagenes === '' ? undefined : form.conImagenes === 'true',
    dadosDeBaja: form.dadosDeBaja ? true : undefined,
  };
}
