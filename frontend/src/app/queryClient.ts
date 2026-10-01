import { QueryClient } from '@tanstack/react-query';
import { isHttpError } from '../shared/api/errors';

const MAX_RETRIES = 2;

/** Reintenta solo errores transitorios (red, timeout, 5xx, 429); nunca 4xx de negocio. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  return isHttpError(error) ? error.isRetryable : false;
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        gcTime: 5 * 60_000,
        refetchOnWindowFocus: false,
        retry: shouldRetry,
        retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
      },
      mutations: {
        retry: false,
      },
    },
  });
}
