import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { config } from '../../shared/config/env';
import { request } from '../../shared/api/httpClient';

const healthSchema = z.object({ status: z.string() });
export type BackendHealth = z.infer<typeof healthSchema>;

export const backendHealthKey = ['system', 'health'] as const;

export async function fetchBackendHealth(signal?: AbortSignal): Promise<BackendHealth> {
  const data = await request<unknown>(config.healthUrl, { absolute: true, signal, timeoutMs: 5000 });
  return healthSchema.parse(data);
}

/** Estado de readiness del backend (para el indicador del encabezado). */
export function useBackendHealth() {
  return useQuery({
    queryKey: backendHealthKey,
    queryFn: ({ signal }) => fetchBackendHealth(signal),
    refetchInterval: 60_000,
    retry: false,
  });
}
