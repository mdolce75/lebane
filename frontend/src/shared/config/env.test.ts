import { describe, expect, it } from 'vitest';
import { parseConfig } from './env';

describe('parseConfig', () => {
  it('aplica valores por defecto', () => {
    expect(parseConfig({})).toEqual({
      apiBaseUrl: '/api',
      healthUrl: '/actuator/health/readiness',
      httpTimeoutMs: 15000,
    });
  });

  it('normaliza la barra final y convierte números', () => {
    expect(parseConfig({ VITE_API_BASE_URL: 'https://api.lebane.test/api/', VITE_HTTP_TIMEOUT_MS: '3000' })).toMatchObject({
      apiBaseUrl: 'https://api.lebane.test/api',
      httpTimeoutMs: 3000,
    });
  });

  it('rechaza timeouts inválidos', () => {
    expect(() => parseConfig({ VITE_HTTP_TIMEOUT_MS: '-1' })).toThrow();
  });
});
