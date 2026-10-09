import { http } from '../../../shared/api/httpClient';
import { parseResponse } from '../../../shared/api/parse';
import {
  consultaCreadaSchema,
  departamentoDetalleSchema,
  imagenSchema,
  paginaConsultasSchema,
  paginaSchema,
  type ConsultaPayload,
  type DepartamentoPayload,
} from './schemas';
import type { ListadoParams } from '../listado/listadoParams';

const BASE = '/departamentos';

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
      superficieMin: params.superficieMin,
      superficieMax: params.superficieMax,
      ambientesMin: params.ambientesMin,
      conImagenes: params.conImagenes,
      dadosDeBaja: params.dadosDeBaja,
      pagina: params.page,
      cantidad: params.size,
      sort: params.sort,
    },
  });
  return parseResponse(paginaSchema, data);
}

export async function obtenerDepartamento(id: number, signal?: AbortSignal) {
  return parseResponse(departamentoDetalleSchema, await http.get<unknown>(`${BASE}/${id}`, { signal }));
}

/**
 * Alta con sus fotos en un solo request multipart: la parte `departamento` lleva el JSON y `imagenes` se repite por
 * cada foto. El backend responde 202 con el departamento creado; si algo falla, no crea nada.
 */
export async function crearDepartamento(payload: DepartamentoPayload, imagenes: File[] = []) {
  const form = new FormData();
  form.append('departamento', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
  imagenes.forEach((imagen) => form.append('imagenes', imagen));
  // Con fotos, el request puede tardar más que una consulta: timeout propio.
  const data = await http.post<unknown>(BASE, form, imagenes.length > 0 ? { timeoutMs: 120_000 } : {});
  return parseResponse(departamentoDetalleSchema, data);
}

/** Edición con concurrencia optimista: `If-Match` con la versión leída (412 si otro usuario la modificó). */
export async function actualizarDepartamento(id: number, version: number, payload: DepartamentoPayload) {
  const data = await http.put<unknown>(`${BASE}/${id}`, payload, { headers: { 'If-Match': `"${version}"` } });
  return parseResponse(departamentoDetalleSchema, data);
}

/**
 * Baja lógica con concurrencia optimista: `If-Match` con la versión leída (412 si otro usuario lo modificó).
 * Sale del listado y no admite cambios hasta reactivarlo; el detalle se sigue pudiendo leer.
 */
export async function darDeBajaDepartamento(id: number, version: number) {
  await http.delete<void>(`${BASE}/${id}`, { headers: { 'If-Match': `"${version}"` } });
}

/** Revierte la baja. Con `If-Match`, como la edición; 409 si la dirección la ocupa otro aviso publicado. */
export async function reactivarDepartamento(id: number, version: number) {
  const data = await http.post<unknown>(`${BASE}/${id}/reactivacion`, undefined, {
    headers: { 'If-Match': `"${version}"` },
  });
  return parseResponse(departamentoDetalleSchema, data);
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

/** Consultas recibidas, de la más reciente a la más antigua, paginadas en el servidor. */
export async function listarConsultas(departamentoId: number, page: number, size: number, signal?: AbortSignal) {
  const data = await http.get<unknown>(`${BASE}/${departamentoId}/consultas`, { signal, query: { page, size } });
  return parseResponse(paginaConsultasSchema, data);
}
