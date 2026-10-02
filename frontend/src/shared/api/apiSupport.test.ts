import { afterEach, describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { HttpError, messageForStatus } from './errors';
import { applyServerFieldErrors } from './fieldErrors';
import { parseResponse } from './parse';
import { generateRequestId } from './requestId';
import { shouldRetry } from '../../app/queryClient';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

const httpError = (status: number) => new HttpError({ kind: 'http', status, message: 'x' });

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('política de reintentos (shouldRetry)', () => {
  it.each([
    ['red', new HttpError({ kind: 'network', message: 'x' })],
    ['timeout', new HttpError({ kind: 'timeout', message: 'x' })],
    ['500', httpError(500)],
    ['503', httpError(503)],
    ['429', httpError(429)],
  ])('reintenta errores transitorios: %s', (_, error) => {
    expect(shouldRetry(0, error)).toBe(true);
    expect(shouldRetry(1, error)).toBe(true);
  });

  it.each([
    ['400', httpError(400)],
    ['404', httpError(404)],
    ['412', httpError(412)],
    ['501', httpError(501)],
    ['respuesta inválida', new HttpError({ kind: 'parse', message: 'x' })],
    ['error que no es HTTP', new Error('boom')],
  ])('nunca reintenta: %s', (_, error) => {
    expect(shouldRetry(0, error)).toBe(false);
  });

  it('se detiene al llegar al máximo de reintentos', () => {
    expect(shouldRetry(2, httpError(503))).toBe(false);
  });
});

describe('messageForStatus', () => {
  it('usa mensajes genéricos y nunca detalles del servidor', () => {
    expect(messageForStatus(404)).toBe('El recurso solicitado no existe.');
    expect(messageForStatus(502)).toBe('Ocurrió un error inesperado.');
    expect(messageForStatus(418)).toBe('No se pudo completar la operación.');
  });
});

describe('parseResponse', () => {
  const schema = z.object({ id: z.number() });

  it('devuelve los datos validados', () => {
    expect(parseResponse(schema, { id: 1, extra: 'ignorado' })).toEqual({ id: 1 });
  });

  it('un desajuste de contrato es un error de respuesta inválida, sin detalles de la validación', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
    let thrown: unknown;
    try {
      parseResponse(schema, { id: 'uno' });
    } catch (error) {
      thrown = error;
    }
    expect(thrown).toBeInstanceOf(HttpError);
    expect(thrown).toMatchObject({ kind: 'parse', message: 'La respuesta del servidor no es válida.' });
    expect((thrown as HttpError).isRetryable).toBe(false);
  });
});

describe('generateRequestId', () => {
  it('genera UUID v4 distintos', () => {
    const a = generateRequestId();
    expect(a).toMatch(UUID_V4);
    expect(generateRequestId()).not.toBe(a);
  });

  it('funciona sin crypto.randomUUID (contextos no seguros, p. ej. http fuera de localhost)', () => {
    const real = globalThis.crypto;
    vi.stubGlobal('crypto', { getRandomValues: (bytes: Uint8Array) => real.getRandomValues(bytes) });
    const id = generateRequestId();
    expect(id).toMatch(UUID_V4);
  });
});

describe('applyServerFieldErrors', () => {
  it('asigna los errores a los campos conocidos y devuelve los demás', () => {
    const setError = vi.fn();
    const error = new HttpError({
      kind: 'http',
      status: 400,
      message: 'x',
      fieldErrors: { 'direccion.ciudad': 'no debe estar vacío', interno: 'otro' },
    });

    const unmatched = applyServerFieldErrors(error, setError, ['direccion.ciudad', 'titulo']);

    expect(setError).toHaveBeenCalledExactlyOnceWith('direccion.ciudad', { type: 'server', message: 'no debe estar vacío' });
    expect(unmatched).toEqual({ interno: 'otro' });
  });

  it('ignora errores que no son HTTP', () => {
    const setError = vi.fn();
    expect(applyServerFieldErrors(new Error('x'), setError, ['titulo'])).toEqual({});
    expect(setError).not.toHaveBeenCalled();
  });
});
