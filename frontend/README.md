# lebane-frontend

Panel de administración de Lebane — React 19 · TypeScript · Vite · TanStack Query · React Hook Form · Zod ·
React Router. Documentación completa (pantallas, decisiones y validación) en el [README raíz](../README.md#frontend).

## Comandos

```bash
npm ci               # instala exactamente package-lock.json
npm run dev          # http://localhost:5173 (proxy de /api y /actuator/health a VITE_DEV_PROXY_TARGET)
npm run typecheck
npm run lint
npm test             # Vitest + Testing Library (jsdom)
npm run test:coverage  # cobertura v8 (coverage/index.html)
npm run build        # dist/ (code splitting por ruta y chunks de librerías)
npm run e2e          # Playwright contra el stack Docker (ver abajo)
```

## E2E (Playwright)

Prueban la aplicación en un navegador real contra el stack completo (nginx, backend, PostgreSQL y MinIO), sin
mocks. Requieren el stack levantado (`docker compose up -d --build` desde la raíz); si no está listo, fallan de
entrada con un mensaje claro.

- Usan el **Chrome instalado** (`channel: 'chrome'`), así que no hace falta `npx playwright install`. Con
  `E2E_BROWSER_CHANNEL=chromium` usan el Chromium de Playwright (p. ej. en CI, después de `npx playwright install chromium`).
- `E2E_BASE_URL` cambia el destino (por defecto `http://localhost:3000`).
- Crean datos reales con un prefijo único por ejecución (`E2E…`) y filtran por él: no dependen del contenido de la
  base. La API no permite borrar departamentos, así que esos datos quedan en la base de desarrollo.
- Ante un fallo guardan captura y *trace* en `test-results/` y un reporte en `playwright-report/`.

## Estructura

| Ruta | Contenido |
|---|---|
| `src/app` | `App`, rutas (alta/detalle/edición lazy), layout, `QueryClient` (reintentos solo para errores transitorios), error boundary |
| `src/shared/api` | Cliente HTTP (`X-Request-Id`, timeout, `HttpError`), validación de respuestas con Zod, `fieldErrors` del servidor → formulario |
| `src/shared/components` | `ErrorMessage`, `Spinner`, `FormField`, `Pagination`, `ImageWithFallback`, `EmptyState` |
| `src/shared/format`, `src/shared/hooks` | Formato de precios/superficie/fechas (es-AR), `useDebouncedValue` |
| `src/features/departamentos/api` | Esquemas del contrato, funciones de la API y hooks de TanStack Query |
| `src/features/departamentos/listado` | Listado: estado en la URL (`useListadoSearch`), filtros, tarjetas |
| `src/features/departamentos/form` | Alta y edición (RHF + Zod con las reglas del backend) |
| `src/features/departamentos/imagenes` | Validación por contenido, selección con vista previa, subida secuencial, gestión de fotos |
| `src/features/departamentos/detalle`, `consultas` | Detalle con galería y formulario de consulta |
| `src/features/direcciones` | Autocompletado de direcciones |
| `src/test` | Setup, `mockApi` (backend simulado que registra requests), fixtures |
| `e2e` | Tests E2E de Playwright (`*.e2e.ts`) y verificación previa del stack |
| `nginx/` | nginx de la imagen Docker (SPA + proxy a la API; re-resuelve el upstream con `NGINX_RESOLVER`) |

Las variables `VITE_*` se embeben en el bundle: nunca deben contener secretos.
