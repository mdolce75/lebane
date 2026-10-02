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
npm run build        # dist/ (code splitting por ruta y chunks de librerías)
```

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
| `nginx/` | nginx de la imagen Docker (SPA + proxy a la API; re-resuelve el upstream con `NGINX_RESOLVER`) |

Las variables `VITE_*` se embeben en el bundle: nunca deben contener secretos.
