import { defineConfig } from '@playwright/test';

/**
 * E2E en un navegador real contra el stack completo (nginx + backend + PostgreSQL + MinIO), no contra mocks.
 *
 * - E2E_BASE_URL: frontend a probar (por defecto el de `docker compose up`).
 * - E2E_BROWSER_CHANNEL: navegador. Por defecto el Chrome instalado (no hace falta `npx playwright install`);
 *   `chromium` usa el que descarga Playwright (p. ej. en CI).
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:3000';
const channel = process.env.E2E_BROWSER_CHANNEL ?? 'chrome';

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  globalSetup: './e2e/global-setup.ts',
  // Los tests crean sus propios datos (títulos únicos), pero comparten la base: en serie es más predecible.
  workers: 1,
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  outputDir: 'test-results',
  use: {
    baseURL,
    channel: channel === 'chromium' ? undefined : channel,
    locale: 'es-AR',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
