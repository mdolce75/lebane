import { request, type FullConfig } from '@playwright/test';

/** Falla rápido y con un mensaje claro si el stack no está levantado o no está listo. */
export default async function globalSetup(config: FullConfig) {
  const baseURL = config.projects[0]?.use.baseURL ?? 'http://localhost:3000';
  const api = await request.newContext({ baseURL });
  try {
    const response = await api.get('/actuator/health/readiness', { timeout: 10_000 }).catch(() => null);
    if (!response?.ok()) {
      throw new Error(
        `El stack no está listo en ${baseURL} (readiness: ${response?.status() ?? 'sin respuesta'}). ` +
          'Levantalo con `docker compose up -d --build` o indicá otro destino con E2E_BASE_URL.',
      );
    }
  } finally {
    await api.dispose();
  }
}
