import { config } from '../config/env';
import { apiErrorSchema, HttpError, messageForStatus } from './errors';
import { generateRequestId, REQUEST_ID_HEADER } from './requestId';

type QueryValue = string | number | boolean | null | undefined;

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Record<string, QueryValue | QueryValue[]>;
  /** Objeto serializable a JSON o FormData (subida de archivos). */
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  timeoutMs?: number;
  /** Si es true, `path` se usa tal cual (no se antepone apiBaseUrl). */
  absolute?: boolean;
};

export function buildUrl(baseUrl: string, path: string, query?: RequestOptions['query']): string {
  const url = `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, raw] of Object.entries(query)) {
    const values = Array.isArray(raw) ? raw : [raw];
    for (const value of values) {
      if (value === undefined || value === null || value === '') continue;
      params.append(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

/**
 * Cliente HTTP centralizado:
 * - Genera y envía X-Request-Id en cada request (correlación con logs del backend).
 * - Timeout con AbortController, combinable con la señal de TanStack Query.
 * - Normaliza errores a HttpError con mensaje seguro para el usuario + requestId.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const requestId = generateRequestId();
  const timeoutMs = options.timeoutMs ?? config.httpTimeoutMs;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(new DOMException('Timeout', 'TimeoutError')), timeoutMs);
  const onExternalAbort = () => controller.abort(options.signal?.reason);
  options.signal?.addEventListener('abort', onExternalAbort, { once: true });

  const headers: Record<string, string> = {
    Accept: 'application/json',
    [REQUEST_ID_HEADER]: requestId,
    ...options.headers,
  };
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body; // el navegador define el boundary del multipart
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }

  const url = options.absolute ? path : buildUrl(config.apiBaseUrl, path, options.query);

  let response: Response;
  try {
    response = await fetch(url, { method: options.method ?? 'GET', headers, body, signal: controller.signal });
  } catch (cause) {
    if (options.signal?.aborted) throw cause; // cancelación legítima (p. ej. TanStack Query)
    const timedOut = controller.signal.aborted;
    throw new HttpError({
      kind: timedOut ? 'timeout' : 'network',
      message: timedOut
        ? 'El servidor tardó demasiado en responder.'
        : 'No se pudo conectar con el servidor. Verificá tu conexión.',
      requestId,
    });
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort', onExternalAbort);
  }

  const responseRequestId = response.headers.get(REQUEST_ID_HEADER) ?? requestId;

  if (!response.ok) {
    throw await toHttpError(response, responseRequestId);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) {
    return (await response.text()) as T;
  }
  try {
    return (await response.json()) as T;
  } catch {
    throw new HttpError({
      kind: 'parse',
      status: response.status,
      message: 'La respuesta del servidor no es válida.',
      requestId: responseRequestId,
    });
  }
}

async function toHttpError(response: Response, requestId: string): Promise<HttpError> {
  let parsed: ReturnType<typeof apiErrorSchema.safeParse> | null = null;
  try {
    parsed = apiErrorSchema.safeParse(await response.json());
  } catch {
    parsed = null;
  }
  const body = parsed?.success ? parsed.data : null;
  return new HttpError({
    kind: 'http',
    status: response.status,
    code: body?.error ?? `HTTP_${response.status}`,
    // Solo se muestran mensajes del contrato ApiError; si no, un mensaje genérico por status.
    message: body?.message ?? messageForStatus(response.status),
    requestId: body?.requestId ?? requestId,
    fieldErrors: body?.fieldErrors ?? {},
  });
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, 'method' | 'body'>) =>
    request<T>(path, { ...options, method: 'DELETE' }),
};
