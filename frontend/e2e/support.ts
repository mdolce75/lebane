import { expect, type APIRequestContext } from '@playwright/test';

const API = '/api/departamentos';

/** PNG 1x1 válido: el backend valida la firma del archivo y el navegador tiene que poder dibujarlo. */
export const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
);

/**
 * Prefijo único por ejecución: los tests crean datos reales (la API no permite borrar departamentos) y filtran
 * por este texto, así no dependen de lo que ya haya en la base ni de otras corridas.
 */
export function marca(): string {
  return `E2E${Date.now().toString(36)}${Math.floor(Math.random() * 1e4)}`;
}

/** Unidad aleatoria (hasta 10 caracteres) para que cada alta de los tests tenga una dirección propia. */
export function unidadUnica(): string {
  return `E${Math.random().toString(36).slice(2, 10)}`;
}

export type DepartamentoCreado = { id: number; titulo: string };

export async function crearDepartamento(
  api: APIRequestContext,
  overrides: Record<string, unknown> = {},
): Promise<DepartamentoCreado> {
  const response = await api.post(API, {
    data: {
      titulo: `${marca()} departamento`,
      descripcion: 'Creado por los tests E2E',
      precio: 100000,
      moneda: 'USD',
      ambientes: 3,
      dormitorios: 2,
      banos: 1,
      superficieM2: 65,
      estado: 'DISPONIBLE',
      // Unidad propia por alta: la API rechaza dos departamentos publicados en la misma dirección.
      direccion: { calle: 'Gorriti', numero: '4850', unidad: unidadUnica(), ciudad: 'Ciudad Autónoma de Buenos Aires', provincia: 'CABA' },
      ...overrides,
    },
  });
  expect(response.status(), await response.text()).toBe(202);
  return (await response.json()) as DepartamentoCreado;
}

export async function obtenerDepartamento(api: APIRequestContext, id: number) {
  const response = await api.get(`${API}/${id}`);
  expect(response.ok()).toBe(true);
  return { body: (await response.json()) as Record<string, unknown>, etag: response.headers()['etag'] ?? '' };
}
