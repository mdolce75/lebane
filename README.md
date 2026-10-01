# Lebane

Aplicación inmobiliaria para gestionar **departamentos en venta**: API REST (Spring Boot 3 / Java 21 /
PostgreSQL / MinIO) y panel de administración (React + TypeScript + Vite), orquestados con Docker Compose y con
un perfil opcional de observabilidad (Elasticsearch + Logstash + Kibana).

> **Estado:** Fase 1 implementada (infraestructura, Actuator, health probes, logging JSON, correlation ID,
> seguridad de Actuator, Docker Compose, scaffold del frontend). Ver [Estado por fase](#estado-por-fase) y
> [Limitaciones conocidas](#limitaciones-conocidas): **la Fase 1 todavía no fue compilada ni testeada** porque el
> entorno donde se escribió no tenía acceso a Maven Central, npm ni Docker Hub.

---

## Tabla de contenidos

- [Arquitectura](#arquitectura)
- [Requisitos](#requisitos)
- [Variables de entorno](#variables-de-entorno)
- [Ejecución con Docker Compose](#ejecución-con-docker-compose)
- [Ejecución sin Docker](#ejecución-sin-docker)
- [Perfil de observabilidad](#perfil-de-observabilidad)
- [Endpoints](#endpoints)
- [Actuator, liveness y readiness](#actuator-liveness-y-readiness)
- [Logging estructurado](#logging-estructurado)
- [Correlation ID](#correlation-id)
- [Política de datos sensibles](#política-de-datos-sensibles)
- [Tests](#tests)
- [Decisiones técnicas](#decisiones-técnicas)
- [Estado por fase](#estado-por-fase)
- [Limitaciones conocidas](#limitaciones-conocidas)

---

## Arquitectura

```
                 navegador
                    │  http://localhost:3000
                    ▼
        ┌──────────────────────┐   /api/*, /actuator/health*   ┌──────────────────────────┐
        │ frontend (nginx)     │ ────────────────────────────▶ │ backend (Spring Boot)    │
        │ React SPA estática   │   + X-Request-Id              │ :8080                    │
        └──────────────────────┘                               │  ├─ PostgreSQL (JPA)     │
                                                               │  ├─ MinIO (imágenes)     │
                                                               │  └─ AddressProvider      │
                                                               └────────────┬─────────────┘
                                                                            │ JSON stdout (siempre)
                                                                            │ TCP async (opcional)
                                                                            ▼
                                                     Logstash ─▶ Elasticsearch ─▶ Kibana
                                                     (perfil "observability", opcional)
```

```
/
├── backend/                Spring Boot 3.5 · Java 21 · Maven
│   └── src/main/java/com/lebane
│       ├── config          Seguridad, CORS, propiedades
│       ├── departamento    controller · dto · entity · mapper · repository · service   (Fase 2-3)
│       ├── address         client · dto · provider · service                          (Fase 4)
│       ├── storage         client · config · service                                  (Fase 4)
│       ├── resilience      Configuración y eventos de Resilience4j                    (Fase 4)
│       ├── logging         filter (X-Request-Id + access log) · interceptor (propagación saliente)
│       ├── exception       ApiError, ErrorCode (+ handler global en Fase 2)
│       └── seed            Seed idempotente                                           (Fase 2)
├── frontend/               React 19 · TypeScript · Vite · TanStack Query · RHF · Zod · React Router
├── observability/logstash/ Pipeline de Logstash
├── docker-compose.yml
├── .env.example
└── README.md
```

Backend organizado **por dominio** (cada dominio con sus capas). Los controllers nunca exponen entidades JPA.

## Requisitos

| Herramienta | Versión | Uso |
|---|---|---|
| Docker + Docker Compose v2 | 24+ | Ejecución completa y tests de integración (Testcontainers) |
| JDK | 21 | Backend sin Docker |
| Maven | 3.9+ (o el Maven incluido en IntelliJ) | Build/tests del backend |
| Node.js | 20.19+ o 22 | Frontend sin Docker |

## Variables de entorno

Toda la configuración proviene de variables de entorno. `cp .env.example .env` y reemplazar los `change-me`.
`.env` está en `.gitignore`.

| Variable | Default | Descripción |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | — (requeridas) | Base de datos. El backend en Docker deriva `DB_*` de estas. |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/lebane` | Solo para ejecutar el backend fuera de Docker. |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | `10` / `2` | Pool Hikari. |
| `DB_CONNECTION_TIMEOUT_MS` | `3000` | Timeout corto para que readiness no se cuelgue. |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | — (requeridas) | Credenciales de MinIO. |
| `STORAGE_*` | ver `.env.example` | Endpoint, bucket, URL pública y credenciales de storage (Fase 4). |
| `ADDRESS_PROVIDER*` | `stub` | Proveedor de autocomplete de direcciones (Fase 4). |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos (coma). En Docker se usa proxy (mismo origen). |
| `ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD` | `actuator` / — (requerida en Docker) | HTTP Basic para `/actuator/metrics` y `/actuator/prometheus`. |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | Muestreo de trazas (traceId/spanId siempre se generan para logs). |
| `LOG_LEVEL` / `LOG_LEVEL_APP` | `INFO` | Nivel root / paquete `com.lebane`. |
| `LOG_FORMAT` | `json` | `json` (producción/Docker) o `text` (desarrollo). |
| `LOG_SERVICE_NAME` / `LOG_ENVIRONMENT` | `lebane-backend` / `local` | Campos `service` / `environment` en cada log. |
| `LOGSTASH_ENABLED` / `LOGSTASH_HOST` / `LOGSTASH_PORT` | `false` / `logstash` / `5000` | Envío opcional a Logstash. |
| `VITE_API_BASE_URL` | `/api` | Base de la API vista por el navegador (build time, no secretos). |
| `BACKEND_UPSTREAM` | `http://backend:8080` | Upstream de nginx (red interna Docker). |
| `ELASTIC_VERSION` / `ELASTIC_PASSWORD` / `KIBANA_SYSTEM_PASSWORD` / `KIBANA_ENCRYPTION_KEY` | ver `.env.example` | Perfil de observabilidad. |
| `*_HOST_PORT` | ver `.env.example` | Puertos publicados en el host. |

## Ejecución con Docker Compose

```bash
cp .env.example .env          # editar los valores change-me
docker compose up -d --build
docker compose ps             # esperar a que backend y frontend estén "healthy"
```

| Servicio | URL | Notas |
|---|---|---|
| Frontend | http://localhost:3000 | nginx; hace proxy de `/api` y `/actuator/health*` al backend |
| Backend | http://localhost:8080 | API + Actuator |
| MinIO API | http://localhost:9000 | URLs públicas de imágenes (Fase 4) |
| MinIO consola | http://127.0.0.1:9001 | Solo localhost |
| PostgreSQL | 127.0.0.1:5432 | Solo localhost |

Healthchecks: PostgreSQL (`pg_isready`), MinIO (`mc ready local`), backend (`wget` a
`/actuator/health/readiness`, la imagen Alpine de Temurin incluye `wget` de busybox) y frontend (`wget` a `/`).
`backend` espera a `postgres` y `minio` sanos; `frontend` espera a `backend` sano.

Logs de contenedores: driver `json-file` con rotación (`max-size: 10m`, `max-file: 5`).

## Ejecución sin Docker

```bash
# Infraestructura mínima
docker compose up -d postgres minio

# Backend (otra terminal)
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/lebane DB_USERNAME=lebane DB_PASSWORD=<tu-password>
export ACTUATOR_PASSWORD=<algo> LOG_FORMAT=text
mvn spring-boot:run

# Frontend (otra terminal) — vite redirige /api y /actuator/health a localhost:8080
cd frontend
npm install
npm run dev        # http://localhost:5173
```

En Windows (PowerShell): `$env:DB_PASSWORD="..."` en lugar de `export`.

## Perfil de observabilidad

```bash
# En .env: LOGSTASH_ENABLED=true (opcional: sin esto ELK levanta pero no recibe logs)
docker compose --profile observability up -d --build
```

| Servicio | URL | Credenciales |
|---|---|---|
| Elasticsearch | http://127.0.0.1:9200 | `elastic` / `ELASTIC_PASSWORD` |
| Kibana | http://127.0.0.1:5601 | `elastic` / `ELASTIC_PASSWORD` |
| Logstash TCP | 127.0.0.1:5000 | entrada `json_lines` |

- `elasticsearch-setup` es un contenedor efímero que fija la contraseña de `kibana_system`.
- Índices: `lebane-logs-YYYY.MM.dd`. En Kibana crear un *data view* `lebane-logs-*` con campo de tiempo `@timestamp`.
- Volúmenes persistentes: `elasticsearch-data`, `logstash-data`, `kibana-data`.
- El backend **no depende** de estos servicios: no están en `depends_on` ni en readiness. Si Logstash cae, el
  appender asíncrono descarta eventos (nunca bloquea requests) y reintenta conectar cada 10 s; stdout sigue
  recibiendo todos los logs.

## Endpoints

La API de dominio (`/api/v1/departamentos`, imágenes, consultas, autocomplete) se implementa en las Fases 2–4 y
se documentará aquí con contratos, paginación, filtros y ejemplos.

Esquema de error común (`ApiError`):

```json
{
  "timestamp": "2026-01-01T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "La solicitud contiene datos inválidos",
  "path": "/api/v1/departamentos",
  "requestId": "req-456",
  "fieldErrors": { "price": "Debe ser mayor que cero" }
}
```

## Actuator, liveness y readiness

| Endpoint | Acceso | Respuesta |
|---|---|---|
| `GET /actuator/health` | público | `{"status":"UP"}` (sin detalles ni componentes) |
| `GET /actuator/health/liveness` | público | `{"status":"UP"}` |
| `GET /actuator/health/readiness` | público | `{"status":"UP"}` / **503** `{"status":"DOWN"}` |
| `GET /actuator/info` | público | build + versión de Java |
| `GET /actuator/metrics` | HTTP Basic (`ACTUATOR_*`) | métricas Micrometer |
| `GET /actuator/prometheus` | HTTP Basic (`ACTUATOR_*`) | formato Prometheus |
| `env`, `configprops`, `beans`, `mappings`, `threaddump`, `heapdump`, `loggers`, `shutdown`… | **no expuestos** | 404 |

Configuración clave (`application.yml`): `management.endpoints.access.default=none` (todo deshabilitado salvo lo
explícito), `show-details: never`, `show-components: never`, `info.env.enabled=false`.

**Liveness vs readiness**

- **Liveness** (`livenessState`): ¿el proceso está vivo? No incluye **ninguna** dependencia externa. Una caída
  de PostgreSQL, MinIO, Logstash o del proveedor de direcciones **no** debe provocar un reinicio del contenedor.
- **Readiness** (`readinessState` + `db`): ¿puede atender tráfico? Incluye solo dependencias críticas. Si
  PostgreSQL no responde → **503**. Logstash/Elasticsearch/Kibana y proveedores opcionales nunca afectan
  readiness. *MinIO se evaluará para readiness en la Fase 4 (solo si se considera crítico para las operaciones
  principales; la lectura del listado no lo requiere).*

```bash
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
curl -u actuator:$ACTUATOR_PASSWORD http://localhost:8080/actuator/prometheus | head
```

Para aislar Actuator en red interna se puede definir `MANAGEMENT_SERVER_PORT` (por ejemplo `8081`) y no
publicarlo en el host (ajustar el healthcheck al nuevo puerto).

Métricas disponibles en Fase 1: HTTP server (`http_server_requests_seconds`, con histogramas y SLOs), JVM, CPU,
Hikari (`hikaricp_*`), Tomcat, logback (eventos por nivel). Tags comunes: `application`, `environment`.
Resilience4j y storage se agregan en la Fase 4.

## Logging estructurado

| `LOG_FORMAT` | Salida | Archivo |
|---|---|---|
| `json` (default) | JSON por línea a stdout (Logstash Logback Encoder) | `logback-prod.xml` |
| `text` | Texto legible con requestId/traceId | `logback-dev.xml` |
| + `LOGSTASH_ENABLED=true` | Además TCP asíncrono a Logstash | `logback-logstash.xml` |

`logback-spring.xml` selecciona los archivos según las variables (condicionales con Janino).

Ejemplo de access log:

```json
{"@timestamp":"2026-10-01T12:00:00.123-03:00","message":"HTTP GET /api/v1/departamentos -> 200 in 12 ms",
 "logger":"com.lebane.access","thread":"tomcat-handler-3","level":"INFO",
 "requestId":"3f0c…","traceId":"6512…","spanId":"9a1b…",
 "method":"GET","path":"/api/v1/departamentos","status":200,"durationMs":12,
 "remoteAddress":"172.18.0.5","responseSize":2048,
 "service":"lebane-backend","application":"lebane-backend","environment":"local"}
```

Campos: `@timestamp`, `level`, `logger`, `thread`, `message`, `service`, `application`, `environment`,
`requestId`, `traceId`, `spanId` (MDC) y campos estructurados por evento (`method`, `path`, `status`,
`durationMs`, `remoteAddress`, `responseSize`; desde Fase 2/4: `errorCode`, `circuitBreaker`, `provider`, …).
Las excepciones van en `exception` (stack trace acortado, causa raíz primero). Nunca se concatenan campos en el
mensaje: se usan `StructuredArguments`.

Niveles: `ERROR` fallos inesperados · `WARN` degradación (CB abierto, fallback, timeout, 5xx) · `INFO` ciclo de
vida/negocio y access log · `DEBUG` detalle técnico (incluye probes de health y scraping de Prometheus).

## Correlation ID

`RequestIdFilter` (orden `HIGHEST_PRECEDENCE + 10`, después del filtro de observación de Micrometer y antes de
Spring Security):

1. Lee `X-Request-Id`; si falta o no cumple `^[A-Za-z0-9._:-]{1,128}$` (evita log injection) genera un UUID.
2. Lo devuelve en el header de respuesta, lo coloca en el MDC (`requestId`) y como atributo del request.
3. Escribe el access log al finalizar y **siempre** limpia el MDC.

`traceId`/`spanId` los aporta Micrometer Tracing (Brave, propagación W3C `traceparent`), sin exporter.
`RequestIdPropagationInterceptor` propaga `X-Request-Id` a llamadas HTTP salientes. El frontend genera un
`X-Request-Id` por request y nginx lo reenvía (o genera uno si falta). Los errores mostrados al usuario incluyen
el requestId como "código de seguimiento".

## Política de datos sensibles

- Nunca se registran: contraseñas, tokens, API keys, credenciales de MinIO, cookies, headers `Authorization`,
  bodies, contenido de archivos, query strings ni URLs firmadas completas.
- El access log solo registra método, path (sin query), status, duración, IP remota y tamaño.
- Defensa en profundidad: `MaskingJsonGeneratorDecorator` enmascara campos (`password`, `secret*`, `accessKey`,
  `token`, `apiKey`, `authorization`, `cookie`…) y valores (`password=…`, `Bearer …`, `X-Amz-Signature=…`,
  `X-Amz-Credential=…`) en todos los logs JSON. Logstash además elimina esos campos.
- Errores HTTP sin stack traces, SQL ni detalles de infraestructura (`server.error.include-*: never`).
- Actuator sin detalles de health; endpoints sensibles no expuestos.
- Secretos solo por variables de entorno; `.env` ignorado por Git.

## Tests

```bash
# Backend: unitarios (*Test, no requieren Docker)
cd backend && mvn test
# Backend: unitarios + integración (*IT, Testcontainers: requiere Docker)
cd backend && mvn verify

# Frontend
cd frontend && npm install && npm run typecheck && npm run lint && npm test && npm run build
```

Tests de la Fase 1:

| Test | Verifica |
|---|---|
| `RequestIdFilterTest` | reutiliza/genera/sanitiza `X-Request-Id`, MDC limpio incluso ante excepciones |
| `RequestIdPropagationInterceptorTest` | propagación del requestId a llamadas salientes |
| `ProbesWithoutDatabaseTest` | PostgreSQL caído ⇒ liveness 200 UP, readiness **503**, health sin detalles |
| `ActuatorSecurityTest` | health/info públicos, metrics/prometheus 401 sin credenciales y 200 con ellas, endpoints sensibles 404, error JSON con requestId |
| `JsonLoggingTest` | cada línea es JSON válido, campos del access log, requestId, enmascarado de secretos |
| `ActuatorEndpointsIT` | con PostgreSQL real (Testcontainers): health/liveness/readiness 200 `{"status":"UP"}`, métricas Hikari/HTTP |
| Frontend `httpClient.test.ts` | X-Request-Id, JSON/FormData, normalización de errores sin detalles internos, timeout, red |
| Frontend `App.test.tsx` | routing, indicador de readiness (UP / 503), 404, política de reintentos |
| Frontend `ErrorMessage.test.tsx`, `env.test.ts` | errores seguros con requestId, validación de configuración con Zod |

## Decisiones técnicas

- **Spring Boot 3.5.x + Java 21**, virtual threads habilitados (`spring.threads.virtual.enabled`).
- **Maven** (sin wrapper commiteado: el entorno de generación no tenía acceso a Maven Central; ver limitaciones).
- **Seguridad**: la API de dominio es pública (el desafío no define usuarios); Spring Security se usa para proteger
  Actuator con HTTP Basic, stateless, sin CSRF (no hay cookies de sesión). Si falta `ACTUATOR_PASSWORD` se usa una
  contraseña aleatoria no registrada (metrics/prometheus quedan inaccesibles) — *fail-safe*.
- **Readiness = readinessState + db**. MinIO se decidirá en la Fase 4.
- **Hikari `connection-timeout` 3 s** para que el probe de readiness responda dentro del timeout del healthcheck (5 s).
- **Logstash** vía `LogstashTcpSocketAppender` (ring buffer asíncrono, `appendTimeout=0` ⇒ descarta en vez de
  bloquear). `NopStatusListener` evita que los errores de reconexión inunden stdout.
- **Esquema de BD**: `ddl-auto=validate`; las migraciones versionadas se agregan en la Fase 2.
- **open-in-view deshabilitado**, `fail_on_pagination_over_collection_fetch=true` (previene paginación en memoria).
- **Frontend en Docker** servido por nginx unprivileged con proxy a la API ⇒ mismo origen, sin CORS.
- **MinIO**: imagen fijada a un release concreto porque MinIO dejó de publicar imágenes community nuevas a fines de
  2025; configurable con `MINIO_IMAGE`.

## Estado por fase

| Fase | Estado |
|---|---|
| 1. Infraestructura y observabilidad inicial | Implementada. **Pendiente: compilar y ejecutar tests** (ver limitaciones) |
| 2. Modelo, persistencia y errores | Pendiente |
| 3. Specifications, Criteria y listado optimizado | Pendiente |
| 4. Storage, direcciones, resiliencia y métricas | Pendiente |
| 4.1 Logging y observabilidad (validación) | Pendiente |
| 5. Frontend | Pendiente (scaffold, cliente HTTP y routing listos) |
| 6. Tests completos | Pendiente |
| 7. Validación final | Pendiente |

## Limitaciones conocidas

- **Fase 1 no compilada ni testeada todavía.** El entorno donde se generó no tenía acceso a Maven Central, al
  registro de npm ni a Docker Hub. Se validó offline: sintaxis de todos los `.java` (parser de javac), sintaxis de
  todos los `.ts/.tsx` (compilador de TypeScript), XML/YAML/JSON bien formados y `docker compose config` (con y sin
  perfil). Ejecutar los comandos de [Tests](#tests) y [Docker Compose](#ejecución-con-docker-compose) antes de
  continuar con la Fase 2.
- Versiones de dependencias fijadas sin poder consultar los repositorios: Spring Boot `3.5.7`, Logstash Logback
  Encoder `8.1`, Janino `3.1.12`, Elastic `8.19.4`; rangos `^` en npm. Si alguna no resolviera, subir al último
  patch disponible.
- No hay `package-lock.json` ni Maven Wrapper: generarlos con `npm install` y `mvn wrapper:wrapper` y commitearlos.
- La API de dominio, MinIO, Resilience4j y el seed llegan en las fases siguientes.
