import { afterEach, describe, expect, it, vi } from 'vitest';
import { buildUrl, http, request } from './httpClient';
import { HttpError } from './errors';
import { jsonResponse } from '../../test/utils';

function mockFetch(impl: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>) {
  const fn = vi.fn(impl);
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('buildUrl', () => {
  it('omite parámetros vacíos y repite arrays', () => {
    expect(buildUrl('/api', '/departamentos', { page: 0, q: '', ambientes: [2, 3], x: undefined })).toBe(
      '/api/departamentos?page=0&ambientes=2&ambientes=3',
    );
  });

  it('agrega la barra inicial si falta', () => {
    expect(buildUrl('/api', 'departamentos')).toBe('/api/departamentos');
  });
});

describe('request', () => {
  it('envía un X-Request-Id en cada request', async () => {
    const fetchMock = mockFetch(async () => jsonResponse({ ok: true }));

    await http.get('/ping');

    const init = fetchMock.mock.calls[0]?.[1];
    const headers = init?.headers as Record<string, string>;
    expect(headers['X-Request-Id']).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('serializa JSON en POST', async () => {
    const fetchMock = mockFetch(async () => jsonResponse({ id: 1 }, { status: 201 }));

    const result = await http.post<{ id: number }>('/x', { a: 1 });

    expect(result).toEqual({ id: 1 });
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.body).toBe('{"a":1}');
    expect((init?.headers as Record<string, string>)['Content-Type']).toBe('application/json');
  });

  it('no fija Content-Type para FormData', async () => {
    const fetchMock = mockFetch(async () => jsonResponse({}));
    const form = new FormData();
    form.append('file', new Blob(['x'], { type: 'image/png' }), 'a.png');

    await http.post('/upload', form);

    const init = fetchMock.mock.calls[0]?.[1];
    expect((init?.headers as Record<string, string>)['Content-Type']).toBeUndefined();
    expect(init?.body).toBe(form);
  });

  it('devuelve undefined en 204', async () => {
    mockFetch(async () => new Response(null, { status: 204 }));
    await expect(http.delete('/x/1')).resolves.toBeUndefined();
  });

  it('normaliza errores ApiError con requestId y fieldErrors', async () => {
    mockFetch(async () =>
      jsonResponse(
        {
          status: 400,
          error: 'VALIDATION_ERROR',
          message: 'La solicitud contiene datos inválidos',
          requestId: 'req-456',
          fieldErrors: { price: 'Debe ser mayor que cero' },
        },
        { status: 400 },
      ),
    );

    const error = await http.post('/x', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(HttpError);
    const httpError = error as HttpError;
    expect(httpError.status).toBe(400);
    expect(httpError.code).toBe('VALIDATION_ERROR');
    expect(httpError.requestId).toBe('req-456');
    expect(httpError.fieldErrors).toEqual({ price: 'Debe ser mayor que cero' });
    expect(httpError.isRetryable).toBe(false);
  });

  it('usa un mensaje genérico si el cuerpo no es ApiError (no expone detalles internos)', async () => {
    mockFetch(async () => new Response('<html>java.lang.NullPointerException</html>', { status: 500 }));

    const error = (await http.get('/x').catch((e: unknown) => e)) as HttpError;

    expect(error.message).toBe('Ocurrió un error inesperado.');
    expect(error.message).not.toContain('NullPointer');
    expect(error.isRetryable).toBe(true);
    expect(error.requestId).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('prefiere el X-Request-Id devuelto por el servidor', async () => {
    mockFetch(async () => new Response('', { status: 503, headers: { 'X-Request-Id': 'srv-1' } }));

    const error = (await http.get('/x').catch((e: unknown) => e)) as HttpError;

    expect(error.requestId).toBe('srv-1');
    expect(error.message).toBe('El servicio no está disponible en este momento.');
  });

  it('convierte fallas de red en HttpError reintentable', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });

    const error = (await http.get('/x').catch((e: unknown) => e)) as HttpError;

    expect(error.kind).toBe('network');
    expect(error.isRetryable).toBe(true);
  });

  it('aborta por timeout', async () => {
    mockFetch(
      (_input, init) =>
        new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
        }),
    );

    const error = (await request('/lento', { timeoutMs: 10 }).catch((e: unknown) => e)) as HttpError;

    expect(error.kind).toBe('timeout');
  });
});
