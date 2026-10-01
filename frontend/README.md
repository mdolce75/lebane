# lebane-frontend

Panel de administración de Lebane — React 19 · TypeScript · Vite · TanStack Query · React Hook Form · Zod ·
React Router. Documentación completa en el [README raíz](../README.md).

## Comandos

```bash
npm install
npm run dev          # http://localhost:5173 (proxy de /api y /actuator/health a VITE_DEV_PROXY_TARGET)
npm run typecheck
npm run lint
npm test             # Vitest + Testing Library (jsdom)
npm run build        # dist/
```

## Estructura

| Ruta | Contenido |
|---|---|
| `src/app` | `App`, rutas, layout, `QueryClient` (reintentos solo para errores transitorios), error boundary |
| `src/shared/api` | Cliente HTTP centralizado (`X-Request-Id`, timeout, `HttpError` normalizado) |
| `src/shared/config` | Variables `VITE_*` validadas con Zod |
| `src/shared/components` | Componentes reutilizables (`ErrorMessage`, `Spinner`) |
| `src/features/system` | Indicador de disponibilidad del backend (readiness) |
| `src/pages` | Páginas; el listado/alta/detalle/edición de departamentos llega en la Fase 5 |
| `nginx/` | Configuración de nginx para la imagen Docker (SPA + proxy a la API; re-resuelve el upstream por DNS con `NGINX_RESOLVER`) |

Las variables `VITE_*` se embeben en el bundle: nunca deben contener secretos.
