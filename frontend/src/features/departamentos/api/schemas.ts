import { z } from 'zod';

/**
 * Contrato de la API de departamentos, validado en runtime: si el backend cambiara un campo, el error aparece en
 * un lugar claro ("respuesta inválida") en vez de propagarse como `undefined` por la UI.
 */
export const monedas = ['ARS', 'USD'] as const;
export const estados = ['DISPONIBLE', 'RESERVADO', 'VENDIDO'] as const;
export const camposOrden = ['createdAt', 'precio', 'superficieM2'] as const;

export const monedaSchema = z.enum(monedas);
export const estadoSchema = z.enum(estados);

export type Moneda = z.infer<typeof monedaSchema>;
export type Estado = z.infer<typeof estadoSchema>;

const decimal = z.coerce.number();

export const direccionSchema = z.object({
  calle: z.string(),
  numero: z.string(),
  piso: z.string().nullable(),
  unidad: z.string().nullable(),
  ciudad: z.string(),
  provincia: z.string(),
  codigoPostal: z.string().nullable(),
  latitud: decimal.nullable(),
  longitud: decimal.nullable(),
  placeId: z.string().nullable(),
});

export const imagenSchema = z.object({
  id: z.number(),
  url: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  posicion: z.number(),
});

export const departamentoDetalleSchema = z.object({
  id: z.number(),
  codigo: z.string(),
  titulo: z.string(),
  descripcion: z.string().nullable(),
  precio: decimal,
  moneda: monedaSchema,
  ambientes: z.number(),
  dormitorios: z.number(),
  banos: z.number(),
  superficieM2: decimal,
  estado: estadoSchema,
  direccion: direccionSchema,
  imagenes: z.array(imagenSchema),
  cantidadConsultas: z.number(),
  version: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
  /** Baja lógica: fecha de baja, o null si está publicado. */
  fechaBaja: z.string().nullable(),
});

export const departamentoItemSchema = z.object({
  id: z.number(),
  codigo: z.string(),
  titulo: z.string(),
  precio: decimal,
  moneda: monedaSchema,
  ambientes: z.number(),
  dormitorios: z.number(),
  banos: z.number(),
  superficieM2: decimal,
  estado: estadoSchema,
  ciudad: z.string(),
  provincia: z.string(),
  imagenPrincipalUrl: z.string().nullable(),
  cantidadImagenes: z.number(),
  cantidadConsultas: z.number(),
  createdAt: z.string(),
  fechaBaja: z.string().nullable(),
});

export const paginaSchema = z.object({
  content: z.array(departamentoItemSchema),
  page: z.object({
    size: z.number(),
    number: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  }),
});

export const consultaCreadaSchema = z.object({
  id: z.number(),
  departamentoId: z.number(),
  createdAt: z.string(),
});

export type Direccion = z.infer<typeof direccionSchema>;
export type Imagen = z.infer<typeof imagenSchema>;
export type DepartamentoDetalle = z.infer<typeof departamentoDetalleSchema>;
export type DepartamentoItem = z.infer<typeof departamentoItemSchema>;
export type Pagina = z.infer<typeof paginaSchema>;

/** Cuerpo de alta y edición (DepartamentoRequest del backend). */
export type DepartamentoPayload = {
  titulo: string;
  descripcion: string | null;
  precio: number;
  moneda: Moneda;
  ambientes: number;
  dormitorios: number;
  banos: number;
  superficieM2: number;
  estado: Estado;
  direccion: {
    calle: string;
    numero: string;
    piso: string | null;
    unidad: string | null;
    ciudad: string;
    provincia: string;
    codigoPostal: string | null;
    latitud: number | null;
    longitud: number | null;
    placeId: string | null;
  };
};

export type ConsultaPayload = {
  nombre: string;
  email: string;
  telefono: string | null;
  mensaje: string;
};
