import { vi } from 'vitest';
import { jsonResponse } from './utils';

export type RecordedCall = {
  method: string;
  url: URL;
  headers: Headers;
  body: unknown;
};

type Responder = (call: RecordedCall) => Response | Promise<Response>;
type Route = { method: string; path: string | RegExp; respond: Responder };

/**
 * Backend simulado para tests de pantallas: resuelve requests por método y ruta, registra cada llamada (URL con
 * query, headers y body) y falla explícitamente ante requests no previstos.
 */
export function mockApi() {
  const routes: Route[] = [
    { method: 'GET', path: '/actuator/health/readiness', respond: () => jsonResponse({ status: 'UP' }) },
  ];
  const calls: RecordedCall[] = [];

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(String(input), 'http://localhost');
    const method = (init.method ?? 'GET').toUpperCase();
    const body = typeof init.body === 'string' ? JSON.parse(init.body) : init.body;
    const call: RecordedCall = { method, url, headers: new Headers(init.headers), body };
    calls.push(call);
    // La última ruta registrada tiene prioridad (permite redefinir respuestas dentro de un test).
    const route = [...routes].reverse().find((r) => r.method === method && matches(r.path, url.pathname));
    if (!route) {
      return jsonResponse({ status: 500, error: 'UNMOCKED', message: `${method} ${url.pathname} sin mock` }, { status: 500 });
    }
    return route.respond(call);
  });
  vi.stubGlobal('fetch', fetchMock);

  const api = {
    calls,
    on(method: string, path: string | RegExp, respond: Responder | unknown) {
      routes.push({
        method,
        path,
        respond: typeof respond === 'function' ? (respond as Responder) : () => jsonResponse(respond),
      });
      return api;
    },
    /** Llamadas a una ruta de la API (sin el health check del encabezado). */
    requests(method: string, path: string | RegExp) {
      return calls.filter((c) => c.method === method && matches(path, c.url.pathname));
    },
  };
  return api;
}

export function apiError(status: number, error: string, extra: Record<string, unknown> = {}) {
  return jsonResponse(
    { timestamp: '2026-10-01T12:00:00Z', status, error, message: extra.message ?? 'Error de prueba', requestId: 'req-test-1', ...extra },
    { status, headers: { 'X-Request-Id': 'req-test-1' } },
  );
}

function matches(path: string | RegExp, pathname: string) {
  return typeof path === 'string' ? path === pathname : path.test(pathname);
}

/** Archivo con firma PNG real (detección por contenido). */
export function pngFile(name = 'foto.png', size = 2048) {
  const bytes = new Uint8Array(size);
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  return new File([bytes], name, { type: 'image/png' });
}

export function textFile(name = 'no-es-foto.png') {
  return new File(['<html>no soy una foto</html>'], name, { type: 'image/png' });
}
