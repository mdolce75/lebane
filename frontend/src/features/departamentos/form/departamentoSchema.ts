import { z } from 'zod';
import { estados, monedas, type DepartamentoDetalle, type DepartamentoPayload } from '../api/schemas';

/**
 * Formulario de alta y edición. Los inputs se manejan como texto (lo que tipea el usuario) y se validan con las
 * mismas reglas que el backend (DepartamentoRequest), para dar feedback inmediato. El backend vuelve a validar:
 * sus `fieldErrors` se muestran en el mismo campo porque los nombres coinciden (p. ej. `direccion.ciudad`).
 */
const requerido = (max: number) =>
  z.string().trim().min(1, 'Es obligatorio').max(max, `Máximo ${max} caracteres`);
const opcional = (max: number) => z.string().trim().max(max, `Máximo ${max} caracteres`);

const decimal = (enteros: number, mensaje: string) =>
  z
    .string()
    .trim()
    .min(1, 'Es obligatorio')
    .refine((v) => new RegExp(`^\\d{1,${enteros}}([.,]\\d{1,2})?$`).test(v), mensaje)
    .refine((v) => toNumber(v) > 0, 'Debe ser mayor que 0');

const entero = (min: number, max: number) =>
  z
    .string()
    .trim()
    .min(1, 'Es obligatorio')
    .refine((v) => /^\d+$/.test(v) && Number(v) >= min && Number(v) <= max, `Entre ${min} y ${max}`);

const coordenada = (limite: number) =>
  z
    .string()
    .trim()
    .refine(
      (v) => v === '' || (/^-?\d{1,3}(\.\d+)?$/.test(v) && Math.abs(Number(v)) <= limite),
      `Entre -${limite} y ${limite}`,
    );

export const departamentoFormSchema = z
  .object({
    titulo: requerido(120),
    descripcion: opcional(4000),
    precio: decimal(12, 'Hasta 12 enteros y 2 decimales'),
    moneda: z.enum(monedas, { message: 'Elegí una moneda' }),
    ambientes: entero(1, 20),
    dormitorios: entero(0, 19),
    banos: entero(1, 10),
    superficieM2: decimal(6, 'Hasta 6 enteros y 2 decimales'),
    estado: z.enum(estados),
    direccion: z
      .object({
        calle: requerido(120),
        numero: requerido(10),
        piso: opcional(10),
        unidad: opcional(10),
        ciudad: requerido(80),
        provincia: requerido(80),
        codigoPostal: opcional(10).refine((v) => /^[A-Za-z0-9 ]*$/.test(v), 'Solo letras, números y espacios'),
        latitud: coordenada(90),
        longitud: coordenada(180),
        placeId: opcional(200),
      })
      .superRefine((d, ctx) => {
        if ((d.latitud === '') !== (d.longitud === '')) {
          ctx.addIssue({
            code: 'custom',
            path: [d.latitud === '' ? 'latitud' : 'longitud'],
            message: 'Latitud y longitud van juntas',
          });
        }
      }),
  })
  .superRefine((v, ctx) => {
    const ambientes = Number(v.ambientes);
    const dormitorios = Number(v.dormitorios);
    if (Number.isFinite(ambientes) && Number.isFinite(dormitorios) && v.dormitorios !== '' && dormitorios >= ambientes) {
      ctx.addIssue({ code: 'custom', path: ['dormitorios'], message: 'Debe ser menor que la cantidad de ambientes' });
    }
  });

export type DepartamentoFormValues = z.input<typeof departamentoFormSchema>;

export const valoresIniciales: DepartamentoFormValues = {
  titulo: '',
  descripcion: '',
  precio: '',
  moneda: 'USD',
  ambientes: '',
  dormitorios: '',
  banos: '1',
  superficieM2: '',
  estado: 'DISPONIBLE',
  direccion: {
    calle: '',
    numero: '',
    piso: '',
    unidad: '',
    ciudad: '',
    provincia: '',
    codigoPostal: '',
    latitud: '',
    longitud: '',
    placeId: '',
  },
};

function toNumber(value: string): number {
  return Number(value.replace(',', '.'));
}

/** Se guardan con 6 decimales (~10 cm): un proveedor de direcciones puede traer más. */
const aCoordenada = (value: string | undefined) => (value ? Number(Number(value).toFixed(6)) : null);

const nullable = (value: string) => (value.trim() === '' ? null : value.trim());

/** Valores ya validados → cuerpo del request. */
export function toPayload(v: DepartamentoFormValues): DepartamentoPayload {
  const d = v.direccion;
  return {
    titulo: v.titulo.trim(),
    descripcion: nullable(v.descripcion ?? ''),
    precio: toNumber(v.precio),
    moneda: v.moneda,
    ambientes: Number(v.ambientes),
    dormitorios: Number(v.dormitorios),
    banos: Number(v.banos),
    superficieM2: toNumber(v.superficieM2),
    estado: v.estado,
    direccion: {
      calle: d.calle.trim(),
      numero: d.numero.trim(),
      piso: nullable(d.piso ?? ''),
      unidad: nullable(d.unidad ?? ''),
      ciudad: d.ciudad.trim(),
      provincia: d.provincia.trim(),
      codigoPostal: nullable(d.codigoPostal ?? ''),
      latitud: aCoordenada(d.latitud),
      longitud: aCoordenada(d.longitud),
      placeId: nullable(d.placeId ?? ''),
    },
  };
}

/** Departamento del servidor → valores del formulario de edición. */
export function fromDetalle(d: DepartamentoDetalle): DepartamentoFormValues {
  const text = (value: string | number | null | undefined) => (value === null || value === undefined ? '' : String(value));
  return {
    titulo: d.titulo,
    descripcion: text(d.descripcion),
    precio: text(d.precio),
    moneda: d.moneda,
    ambientes: text(d.ambientes),
    dormitorios: text(d.dormitorios),
    banos: text(d.banos),
    superficieM2: text(d.superficieM2),
    estado: d.estado,
    direccion: {
      calle: d.direccion.calle,
      numero: d.direccion.numero,
      piso: text(d.direccion.piso),
      unidad: text(d.direccion.unidad),
      ciudad: d.direccion.ciudad,
      provincia: d.direccion.provincia,
      codigoPostal: text(d.direccion.codigoPostal),
      latitud: text(d.direccion.latitud),
      longitud: text(d.direccion.longitud),
      placeId: text(d.direccion.placeId),
    },
  };
}
