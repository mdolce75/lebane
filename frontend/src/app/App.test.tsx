import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { jsonResponse, renderRoute } from '../test/utils';
import { shouldRetry } from './queryClient';
import { HttpError } from '../shared/api/errors';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('routing', () => {
  it('renderiza la home con el indicador de API disponible', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ status: 'UP' }));
    vi.stubGlobal('fetch', fetchMock);

    renderRoute('/');

    expect(screen.getByRole('heading', { name: 'Panel de departamentos' })).toBeInTheDocument();
    expect(await screen.findByText('API disponible')).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith('/actuator/health/readiness', expect.anything());
  });

  it('muestra API no disponible cuando readiness responde 503', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ status: 'DOWN' }, { status: 503 })));

    renderRoute('/departamentos');

    expect(screen.getByRole('heading', { name: 'Departamentos' })).toBeInTheDocument();
    expect(await screen.findByText('API no disponible')).toBeInTheDocument();
  });

  it('muestra 404 para rutas desconocidas', () => {
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ status: 'UP' })));

    renderRoute('/no-existe');

    expect(screen.getByRole('heading', { name: 'Página no encontrada' })).toBeInTheDocument();
  });
});

describe('shouldRetry', () => {
  it('reintenta errores transitorios y no 4xx', () => {
    expect(shouldRetry(0, new HttpError({ kind: 'network', message: 'x' }))).toBe(true);
    expect(shouldRetry(0, new HttpError({ kind: 'http', status: 503, message: 'x' }))).toBe(true);
    expect(shouldRetry(0, new HttpError({ kind: 'http', status: 400, message: 'x' }))).toBe(false);
    expect(shouldRetry(2, new HttpError({ kind: 'network', message: 'x' }))).toBe(false);
    expect(shouldRetry(0, new Error('x'))).toBe(false);
  });
});
