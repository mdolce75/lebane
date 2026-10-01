import { z } from 'zod';

/** Esquema de error del backend (ApiError). */
export const apiErrorSchema = z.object({
  timestamp: z.string().optional(),
  status: z.number(),
  error: z.string(),
  message: z.string().optional(),
  path: z.string().optional(),
  requestId: z.string().optional(),
  fieldErrors: z.record(z.string(), z.string()).optional(),
});

export type ApiErrorBody = z.infer<typeof apiErrorSchema>;

export type HttpErrorKind = 'http' | 'network' | 'timeout' | 'parse';

/**
 * Error normalizado del cliente HTTP. El mensaje es apto para mostrar al usuario:
 * nunca contiene stack traces ni detalles internos. Incluye requestId para soporte.
 */
export class HttpError extends Error {
  readonly kind: HttpErrorKind;
  readonly status: number | null;
  readonly code: string;
  readonly requestId: string | null;
  readonly fieldErrors: Record<string, string>;

  constructor(params: {
    kind: HttpErrorKind;
    message: string;
    status?: number | null;
    code?: string;
    requestId?: string | null;
    fieldErrors?: Record<string, string>;
  }) {
    super(params.message);
    this.name = 'HttpError';
    this.kind = params.kind;
    this.status = params.status ?? null;
    this.code = params.code ?? 'UNKNOWN_ERROR';
    this.requestId = params.requestId ?? null;
    this.fieldErrors = params.fieldErrors ?? {};
  }

  /** Errores que vale la pena reintentar automáticamente (red, timeout, 5xx salvo 501, 429). */
  get isRetryable(): boolean {
    if (this.kind === 'network' || this.kind === 'timeout') return true;
    if (this.status === null) return false;
    return this.status === 429 || (this.status >= 500 && this.status !== 501);
  }
}

const GENERIC_MESSAGES: Record<number, string> = {
  400: 'La solicitud contiene datos inválidos.',
  401: 'Necesitás autenticarte para continuar.',
  403: 'No tenés permisos para realizar esta acción.',
  404: 'El recurso solicitado no existe.',
  409: 'La operación entra en conflicto con el estado actual.',
  413: 'El archivo o la solicitud supera el tamaño permitido.',
  415: 'El tipo de archivo no es compatible.',
  429: 'Demasiadas solicitudes. Intentá nuevamente en unos segundos.',
  503: 'El servicio no está disponible en este momento.',
};

export function messageForStatus(status: number): string {
  return GENERIC_MESSAGES[status] ?? (status >= 500 ? 'Ocurrió un error inesperado.' : 'No se pudo completar la operación.');
}

export function isHttpError(error: unknown): error is HttpError {
  return error instanceof HttpError;
}
