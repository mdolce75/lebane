# Calidad: tests, CI y validación

[← Volver al README](../README.md)

- [Tests](#tests)
- [Integración continua](#integración-continua)
- [Validación final](#validación-final)
- [Estado por fase](#estado-por-fase)

---

## Tests

Verificación completa de la Fase 1 en Windows (backend, frontend, Docker Compose, probes 200/503, logs JSON y
perfil de observabilidad), con un log por paso y un resumen en `verificacion\resumen.txt`:

```powershell
powershell -ExecutionPolicy Bypass -File .\verificar-fase1.ps1
```

Paso a paso:

```bash
# Backend: unitarios (*Test, no requieren Docker)
cd backend && ./mvnw test
# Backend: unitarios + integración (*IT, Testcontainers: requiere Docker)
cd backend && ./mvnw verify
# Los *IT comparten un único contenedor PostgreSQL (support/PostgresContainer)

# Frontend
cd frontend && npm ci && npm run typecheck && npm run lint && npm test && npm run build

# E2E en un navegador real contra el stack Docker (requiere `docker compose up -d --build`)
cd frontend && npm run e2e
```

### Cobertura

```bash
cd backend && ./mvnw verify            # JaCoCo: unitarios + integración -> backend/target/site/jacoco/index.html
cd frontend && npm run test:coverage   # v8 -> frontend/coverage/index.html
```

| Suite | Tests | Líneas | Ramas |
|---|---|---|---|
| Backend (unitarios + IT) | 252 (209 + 43) | 92,5 % | 74,9 % |
| Frontend (Vitest) | 99 | 96,5 % | 89,8 % |
| E2E (Playwright, stack real) | 7 | — | — |

La cobertura se mide y se publica, pero no se impone un umbral que haga fallar el build: un porcentaje mínimo
empuja a escribir tests para subir el número y no para cubrir riesgos. Lo que queda sin cubrir en el backend es,
sobre todo, `equals`/`hashCode` de entidades, ramas de logging en DEBUG y ramas defensivas de clientes externos.

### E2E (Playwright)

Siete escenarios en Chrome contra nginx + backend + PostgreSQL + MinIO reales, sin mocks: filtros y orden enviados
al servidor con el estado en la URL (incluida la recarga), `X-Request-Id` enviado y devuelto, alta con una foto que
se sube a MinIO y el navegador descarga y decodifica, rechazo de un archivo falso `.jpg` (en el navegador y en la
API), consulta reflejada en el contador del listado, pantallas de no encontrado, y conflicto de edición 412 →
recargar → guardar sin pisar el cambio ajeno. Usan el Chrome instalado (sin `npx playwright install`) y datos con
un prefijo único por ejecución. Detalles en [frontend/README.md](../frontend/README.md#e2e-playwright).

| Test | Verifica |
|---|---|
| `RequestIdFilterTest` | reutiliza/genera/sanitiza `X-Request-Id`, MDC limpio incluso ante excepciones |
| `RequestIdPropagationInterceptorTest` | propagación del requestId a llamadas salientes |
| `ProbesWithoutDatabaseTest` | PostgreSQL caído ⇒ liveness 200 UP, readiness **503**, health sin detalles |
| `ActuatorSecurityTest` | health/info públicos, metrics/prometheus 401 sin credenciales y 200 con ellas, endpoints sensibles 404, error JSON con requestId |
| `JsonLoggingTest` | cada línea es JSON válido, campos del access log, requestId, enmascarado de secretos, sin propiedades internas de Logback, configuración sin warnings |
| `StructuredStatusListenerTest` | estados internos de Logback (p. ej. Logstash caído) reemitidos como JSON, sin stack trace, con rate limit por origen |
| `LoggingPropertiesTest` | `LOG_FORMAT` / `LOGSTASH_ENABLED` inválidos impiden el arranque |
| `ActuatorEndpointsIT` | con PostgreSQL real (Testcontainers): health/liveness/readiness 200 `{"status":"UP"}`, métricas Hikari/HTTP |
| `RequestValidationTest` | reglas de cada campo, `dormitorios < ambientes`, coordenadas completas, consultas, `toString` sin datos personales |
| `DepartamentoMapperTest` | normalización de textos, estado por defecto, edición sin estado, URLs de imágenes |
| `DepartamentoServiceTest`, `ConsultaServiceTest` | alta, detalle con consultas dedicadas, `If-Match`, 404, regla de vendido |
| `DepartamentoControllerTest` | contrato HTTP (201 + `Location` relativo + `ETag`, 412, 409, 404, 405, 415, 503, 500) y esquema `ApiError` sin detalles internos |
| `EntityMappingRulesTest` | ninguna relación EAGER ni colección mapeada |
| `EntityTagsTest`, `CodigoDepartamentoGeneratorTest`, `SeedDataTest` | ETags, formato de códigos, seed válido |
| `PersistenceIT` | Flyway + validación de esquema, auditoría, versión, `CHECK`/`UNIQUE` en la base (máx. 5 fotos, dormitorios, código), orden de imágenes, relaciones LAZY |
| `DepartamentoApiIT` | ciclo HTTP completo contra PostgreSQL, `If-Match`, consultas, errores y **3 sentencias SQL fijas en el detalle** (sin N+1) |
| `DevDataSeederIT` | seed al arrancar, idempotente, sin pisar ediciones |
| `CampoOrdenTest`, `ListadoParamsValidationTest` | órdenes de la lista blanca, defaults, rangos, moneda obligatoria para precio, ventana máxima |
| `DepartamentoListadoServiceTest` | orden de la página preservado, agregados indexados por ID, `COUNT` y agregados omitidos cuando no hacen falta |
| `DepartamentoControllerTest` (listado) | formato `PagedModel`, binding de query params, errores de conversión sin detalles técnicos |
| `ListadoSinConsultasDeTextoTest` | el código del listado (repositorio, Specifications, servicio) no tiene `@Query`, `createQuery(String)`, SQL nativo ni sentencias en literales |
| `ListadoIT` | filtros, orden, paginación, imagen principal y contadores calculados en PostgreSQL, escape de `%`/`_`, 3 sentencias por página |
| `ResilientExecutorTest` | reintentos solo transitorios, timeout con cancelación, apertura y rechazo del circuito, propagación de MDC, logs de eventos con campos y sin mensajes de excepción |
| `ObjectStorageServiceTest`, `ImageTypeTest` | bucket idempotente, reintento reabriendo el stream, 503 sin detalles, compensación que nunca lanza, métricas; detección por magic bytes |
| `ImagenServiceTest` | validaciones antes de subir, primera posición libre, límite bajo lock con compensación, compensación ante error de base, orden de borrado |
| `StubAddressProviderTest`, `ExternalAddressProviderTest` | catálogo local; cliente Georef real contra un servidor HTTP local: mapeo, `X-Request-Id`, 400/401/429/5xx, JSON inválido, timeout |
| `AddressAutocompleteServiceTest` | fallback degradado (WARN/ERROR según el fallo), límite, circuito abierto sin llamar al proveedor, métricas |
| `ImagenControllerTest`, `DireccionControllerTest` | contrato HTTP multipart y autocompletado, errores 400/409/413/503 sin detalles internos |
| `StorageIT` | MinIO real: subida, lectura pública, detalle y listado, límite de 5, contenido no imagen, borrado, seed con fotos idempotente, logs sin credenciales |
| `StorageOutageIT` | MinIO detenido: 503, circuito abierto, rechazo inmediato, readiness/listado/detalle siguen OK, logs de eventos |
| `AddressResilienceIT` | proveedor externo caído → degradado → circuito abierto → rechazo sin red → recuperación `HALF_OPEN` → `CLOSED` |
| Frontend `listadoParams.test.ts`, `useListadoSearch.test.tsx` | URL ↔ filtros (valores inválidos descartados, ventana máxima), cambios rápidos que no se pisan (regresión) |
| Frontend `departamentoSchema.test.ts`, `imageValidation.test.ts`, `format.test.ts` | reglas del formulario iguales al backend, detección de imágenes por contenido, formato de precios |
| Frontend `components.test.tsx` | placeholder e imagen rota, paginación con ventana máxima |
| Frontend `DepartamentosListPage.test.tsx` | paginación y filtros enviados al servidor, validación de filtros, vacío vs sin resultados, error con requestId y reintento |
| Frontend `DepartamentoNuevoPage.test.tsx` | validación, subida secuencial con vista previa, límite de 5, rechazo por contenido, quitar antes de enviar, errores parciales con reintento sin recrear, `fieldErrors` del servidor |
| Frontend `DepartamentoDetallePage.test.tsx`, `DepartamentoEditarPage.test.tsx` | galería, 404, consulta, vendido; `If-Match`, conflicto 412 con recarga, gestión de fotos |
| Frontend `DireccionAutocomplete.test.tsx` | debounce, mínimo de caracteres, proveedor degradado |
| `LogstashAppenderTest` | appender de producción contra un servidor TCP local: JSON con campos comunes, MDC y argumentos, sin propiedades internas, secretos enmascarados; con Logstash caído no bloquea (20.000 eventos) y el apagado no espera más de 5 s |
| `TraceCorrelationTest` | con tracing activo: `requestId`, `traceId` y `spanId` en el access log, `traceparent` entrante continuado, `exception`/`errorCode`/`status` como campos, sin appender de Logstash cuando está deshabilitado |
| `AddressResilienceIT` (correlación) | el proveedor externo recibe `X-Request-Id` y `traceparent` con el mismo `traceId` del request |
| `ListadoPerformanceIT` | 100k departamentos: sin *seq scans* (`pg_stat_user_tables`) y ≤ 3 sentencias en 11 escenarios, con control negativo |
| Frontend `httpClient.test.ts` | X-Request-Id, JSON/FormData, normalización de errores sin detalles internos, timeout, red |
| Frontend `App.test.tsx` | routing, indicador de readiness (UP / 503), 404, política de reintentos |
| Frontend `ErrorMessage.test.tsx`, `env.test.ts` | errores seguros con requestId, validación de configuración con Zod |
| `GlobalExceptionHandlerTest` | multipart inválido, header faltante, JSON inválido vs ausente, 405 sin `Allow`, 406 sin cuerpo, 413, excepciones de Spring con status propio, 409 sin SQL ni constraint, 503 de base, 500 genérico |
| `ApiErrorControllerTest` | errores fuera de Spring MVC (`/error`): código estable y mensaje seguro por status, path original, requestId, sin el mensaje de la excepción |
| `DepartamentoApiIT` (firewall) | un request rechazado por el firewall de Spring Security responde `ApiError` JSON con el requestId del cliente |
| `NonCriticalDependenciesDownIT` | MinIO, Georef y Logstash caídos a la vez: liveness, readiness y health 200 `UP` en menos de 1 s |
| Frontend `apiSupport.test.ts` | política de reintentos (red, timeout, 5xx, 429 sí; 4xx, 501 y contrato inválido no; máximo), respuesta inválida sin detalles, UUID sin `crypto.randomUUID`, `fieldErrors` del servidor |
| Frontend `uploadSequentially.test.ts`, `RouteErrorBoundary.test.tsx` | subida de a una sin concurrencia, errores por foto con requestId; error de render sin detalles técnicos |
| E2E `departamentos.e2e.ts` | ver [E2E](#e2e-playwright) |

## Integración continua

GitHub Actions ([`.github/workflows/ci.yml`](../.github/workflows/ci.yml)) en cada pull request y en cada push a `main`:

| Job | Qué hace |
|---|---|
| **Backend** | `./mvnw verify`: tests unitarios (incluye el contrato OpenAPI) y de integración con PostgreSQL y MinIO reales (Testcontainers), y cobertura JaCoCo, que queda en el resumen de la corrida y como artefacto |
| **Frontend** | `npm ci`, lint, tipos, tests con cobertura, build y `npm audit` de las dependencias de producción (falla con vulnerabilidades altas o críticas) |
| **E2E** | Después de los anteriores: levanta el stack completo con `docker compose up --wait` y corre Playwright con Chromium. Si falla, publica el reporte, las capturas, los *traces* y los logs del stack |

- **Sin credenciales fijas**: el job de E2E genera un `.env` con secretos aleatorios en cada corrida (enmascarados en
  los logs) a partir de `.env.example`.
- **Permisos de solo lectura** (`contents: read`) y cancelación de la corrida anterior al pushear de nuevo.
- **Dependabot** ([`.github/dependabot.yml`](../.github/dependabot.yml)): PRs semanales para Maven, npm, imágenes de
  Docker y GitHub Actions, con versiones menores y parches agrupados; cada PR pasa por el mismo CI. Las versiones
  mayores que requieren una migración planificada se ignoran, cada una con su motivo en el archivo: Spring Boot 4,
  springdoc 3 (requiere Boot 4), MinIO SDK ≥ 8.6 (OkHttp 5 / Kotlin 2), logstash-logback-encoder 9 y Vite 8. Las
  imágenes de Docker se mantienen en la versión del runtime del proyecto (Java 21, Node 22); el salto a otra LTS se
  hace a mano junto con el CI.

## Validación final

Fase 7, sobre el stack Docker reconstruido desde cero (`docker compose down` + `up -d --build`), además de la
compilación limpia y todas las suites:

| Verificación | Resultado |
|---|---|
| `./mvnw clean verify` | BUILD SUCCESS, 0 warnings; 209 unitarios + 43 IT |
| Frontend | lint, typecheck, 99 tests y build OK; 7/7 E2E contra el stack, con control de violaciones de CSP |
| Docker Compose sin perfil / con `--profile observability` | todos los servicios `healthy`; `elasticsearch-setup` y `kibana-setup` terminan con código 0 |
| Probes (directo y vía nginx) | health, liveness y readiness `200 {"status":"UP"}` en ~25 ms, sin detalles |
| PostgreSQL detenido | liveness **200**; readiness, health y API **503** (`SERVICE_UNAVAILABLE`, sin detalles) en ~3 s; vuelve a 200 solo al levantarlo |
| Exposición de Actuator | info público; metrics/prometheus/actuator 401 sin credenciales o con credenciales incorrectas; env, beans, heapdump, configprops, loggers, threaddump y shutdown 404; nginx solo deja pasar health |
| Endpoints | alta 201 con `Location` y `ETag`; detalle; `PUT` con `If-Match` viejo 412 y correcto 200; consulta 201; 404; validaciones 400 por campo; JSON inválido 400 |
| Paginación y filtros | totales y páginas correctos; `size>100`, página fuera de la ventana y `sort` no permitido 400; filtros por estado, moneda, precio, ambientes, fotos y texto verificados sobre cada resultado; orden correcto; `%` escapado |
| MinIO | subida 201 y lectura pública 200; archivo falso 400; 6ª foto 409; 6 MB 413; borrado 204 y objeto 404 |
| MinIO detenido | subidas 503 `STORAGE_UNAVAILABLE` con 3 intentos; el circuito se abre y rechaza en ~20 ms; listado, detalle y readiness 200; recuperación por `HALF_OPEN` |
| Proveedor de direcciones inalcanzable | TimeLimiter de 2 s, 2 intentos, circuito abierto y respuestas degradadas (200, `degradado=true`) en ~15 ms; listado y readiness sin impacto |
| Logstash detenido | latencia del listado sin cambios (p95 70 ms con y sin Logstash); readiness 200; stdout 100 % JSON; un único aviso JSON; eventos de la caída enviados al reconectar |
| Logs | 100 % JSON, campos obligatorios en todos los eventos, `requestId` en todo el access log y en cada evento de request (los únicos sin requestId son del arranque); ningún secreto del `.env` presente, en stdout ni en Elasticsearch |
| Trazabilidad de fallos externos | cada timeout, reintento, apertura de circuito, rechazo y fallback lleva `requestId`, `circuitBreaker`, `provider` y `cause` |
| `EXPLAIN (ANALYZE)` con 100k departamentos | 12 consultas, **0 `Seq Scan`**, de 0,07 a 22 ms (base descartable, eliminada al terminar) |
| N+1 / EAGER / `findAll` | ninguna relación EAGER, ningún `findAll` en el código, sentencias fijas por request (cubierto por `DepartamentoApiIT`, `ListadoIT` y `ListadoPerformanceIT`) |
| CORS | origen permitido 200 con `Access-Control-Allow-Origin`; origen no permitido 403 |
| Código | sin TODO/FIXME, sin prints de depuración, sin clases ni módulos sin uso |

**Corregido durante la validación**

- `/actuator/info` (público) exponía la versión exacta de la JVM: se deshabilitó el contribuidor `java`.
- Backend y API de MinIO publicados en todas las interfaces: ahora solo en `127.0.0.1`.
- Con la base caída, cada probe de readiness registraba un WARN con stack trace de ~5 KB, y Hibernate repetía cada
  error de base con WARN + 2 ERROR por request: ahora queda un único WARN por request fallido.
- nginx no enviaba `Referrer-Policy` en la aplicación ni `X-Frame-Options` en los assets (un `add_header` en un
  `location` anula los del `server`), y duplicaba los headers de seguridad en las respuestas de la API. Se agregó
  además la CSP.
- El README afirmaba que con Logstash caído los eventos se descartaban; en realidad se retienen y se envían al
  reconectar (hasta llenar el buffer).

## Estado por fase

| Fase | Estado |
|---|---|
| 1. Infraestructura y observabilidad inicial | **Completa y verificada** (build, 44 tests unitarios + 5 IT backend, 21 tests frontend, Docker Compose con y sin perfil `observability`) |
| 2. Modelo, persistencia y errores | **Completa y verificada** (109 tests unitarios + 20 IT backend; stack Docker: Flyway, seed idempotente, API directa y vía proxy) |
| 3. Specifications, Criteria y listado optimizado | **Completa y verificada** (131 tests unitarios + 34 IT backend; EXPLAIN ANALYZE con 100k departamentos; endpoint probado directo y vía proxy) |
| 4. Storage, direcciones, resiliencia y métricas | **Completa y verificada** (181 tests unitarios + 41 IT backend, con MinIO real; en Docker: seed con fotos, subida/lectura pública/borrado vía proxy, Georef real, caída y recuperación de MinIO con circuito abierto, métricas en Prometheus) |
| 4.1 Logging y observabilidad (validación) | **Completa y verificada** (187 tests unitarios + 41 IT backend; en Docker: ELK con plantilla, ILM y data view automáticos, búsqueda por `requestId`/`traceId`/campos de resiliencia, ID generado por nginx, Logstash habilitado y deshabilitado, sin secretos indexados) |
| 5. Frontend | **Completa y verificada** (77 tests frontend; typecheck, lint y build; recorrido completo en el navegador contra el stack Docker) |
| 6. Tests completos | **Completa y verificada** (252 (209 + 43) tests backend, 99 frontend y 7 E2E con Playwright contra el stack Docker; cobertura backend 92,5 % de líneas, frontend 96,5 %; dos tests intermitentes corregidos) |
| 7. Validación final | **Completa y verificada** (compilación limpia sin warnings, todas las suites, stack con y sin observabilidad, caídas de PostgreSQL, MinIO, Georef y Logstash, EXPLAIN con 100k filas, auditoría de logs y seguridad; 5 problemas corregidos, ver [Validación final](#validación-final)) |
