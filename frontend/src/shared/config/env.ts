import { z } from 'zod';

/**
 * Configuración del frontend validada con Zod al iniciar.
 * Las variables VITE_* se inyectan en build time (no deben contener secretos).
 */
const envSchema = z.object({
  VITE_API_BASE_URL: z.string().min(1).default('/api'),
  VITE_HEALTH_URL: z.string().min(1).default('/actuator/health/readiness'),
  VITE_HTTP_TIMEOUT_MS: z.coerce.number().int().positive().default(15000),
});

export type AppConfig = {
  apiBaseUrl: string;
  healthUrl: string;
  httpTimeoutMs: number;
};

export function parseConfig(raw: Record<string, unknown>): AppConfig {
  const env = envSchema.parse(raw);
  return {
    apiBaseUrl: env.VITE_API_BASE_URL.replace(/\/+$/, ''),
    healthUrl: env.VITE_HEALTH_URL,
    httpTimeoutMs: env.VITE_HTTP_TIMEOUT_MS,
  };
}

export const config: AppConfig = parseConfig(import.meta.env);
