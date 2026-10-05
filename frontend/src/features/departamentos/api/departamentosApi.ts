import { http } from '../../../shared/api/httpClient';
import { parseResponse } from '../../../shared/api/parse';
import {
  consultaCreadaSchema,
  departamentoDetalleSchema,
  imagenSchema,
  paginaSchema,
  type ConsultaPayload,
  type DepartamentoPayload,
} from './schemas';
import type { ListadoParams } from '../listado/listadoParams';

const BASE = '/v1/departamentos';

/** Filtros, orden y paginación se envían al servidor: el cliente nunca filtra ni pagina en memoria. */
export async function listarDepartamentos(params: ListadoParams, signal?: AbortSignal) {
  const data = await http.get<unknown>(BASE, {
    signal,
    query: {
      q: params.q,
      ciudad: params.ciudad,
      estado: params.estado,
      moneda: params.moneda,
      precioMin: params.precioMin,
      precioMax: params.precioMax,
      ambientesMin: params.ambientesMin,
      conImagenes: params.conImagenes,
      page: params.page,
      size: params.size,
      sort: params.sort,
    },
  });
  return parseResponse(paginaSchema, data);
}

export async function obtenerDepartamento(id: number, signal?: AbortSignal) {
  return parseResponse(departamentoDetalleSchema, await http.get<unknown>(`${BASE}/${id}`, { signal }));
}

export async function crearDepartamento(payload: DepartamentoPayload) {
  return parseResponse(departamentoDetalleSchema, await http.post<unknown>(BASE, payload));
}

/** Edición con concurrencia optimista: `If-Match` con la versión leída (412 si otro usuario la modificó). */
export async function actualizarDepartamento(id: number, version: number, payload: DepartamentoPayload) {
  const data = await http.put<unknown>(`${BASE}/${id}`, payload, { headers: { 'If-Match': `"${version}"` } });
  return parseResponse(departamentoDetalleSchema, data);
}

/**
 * Baja lógica con concurrencia optimista: `If-Match` con la versión leída (412 si otro usuario lo modificó).
 * Desde ahí el departamento responde 404 en toda la API.
 */
export async function darDeBajaDepartamento(id: number, version: number) {
  await http.delete<void>(`${BASE}/${id}`, { headers: { 'If-Match': `"${version}"` } });
}

/** Una foto por request (el backend valida tipo por contenido, tamaño y límite de 5). */
export async function subirImagen(departamentoId: number, archivo: File) {
  const form = new FormData();
  form.append('archivo', archivo);
  // Las subidas pueden tardar más que una consulta: timeout propio.
  const data = await http.post<unknown>(`${BASE}/${departamentoId}/imagenes`, form, { timeoutMs: 60_000 });
  return parseResponse(imagenSchema, data);
}

export async function eliminarImagen(departamentoId: number, imagenId: number) {
  await http.delete<void>(`${BASE}/${departamentoId}/imagenes/${imagenId}`);
}

export async function crearConsulta(departamentoId: number, payload: ConsultaPayload) {
  return parseResponse(consultaCreadaSchema, await http.post<unknown>(`${BASE}/${departamentoId}/consultas`, payload));
}
