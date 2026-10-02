# Instalación y ejecución

[← Volver al README](../README.md)

- [Requisitos](#requisitos)
- [Instalación local](#instalación-local)
- [Variables de entorno](#variables-de-entorno)
- [Ejecución con Docker Compose](#ejecución-con-docker-compose)
- [Ejecución sin Docker](#ejecución-sin-docker)
- [Perfil de observabilidad](#perfil-de-observabilidad)
- [Seed de datos](#seed-de-datos)

---

## Requisitos

| Herramienta | Versión | Uso |
|---|---|---|
| Docker + Docker Compose v2 | 24+ | Ejecución completa y tests de integración (Testcontainers) |
| JDK | 21 | Backend sin Docker |
| Maven | no hace falta: `backend/mvnw` (Maven Wrapper 3.9.11) | Build/tests del backend |
| Node.js | 20.19+ o 22 | Frontend sin Docker, tests y E2E |
| Google Chrome | cualquiera reciente | Solo para los E2E (`npm run e2e`) |

## Instalación local

```bash
git clone https://github.com/mdolce75/lebane.git && cd lebane
cp .env.example .env      # reemplazar cada change-me por un valor propio (contraseñas de PostgreSQL, MinIO,
                          # Actuator y, si se usa el perfil observability, de Elasticsearch/Kibana)
docker compose up -d --build
```

Con eso queda todo funcionando en http://localhost:3000 (con datos de ejemplo, `SEED_ENABLED=true`). Para
desarrollar sin Docker, ver [Ejecución sin Docker](#ejecución-sin-docker); para los tests, `cd backend && ./mvnw
verify` (requiere Docker para Testcontainers) y `cd frontend && npm ci && npm test`.

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
| `STORAGE_PUBLIC_URL` / `STORAGE_BUCKET` | `http://localhost:9000` / `lebane-images` | URL pública con la que el navegador accede a las imágenes. |
| `STORAGE_ENDPOINT` | `http://minio:9000` | URL interna backend → MinIO. |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | — (en Docker: `MINIO_ROOT_*`) | Credenciales de MinIO; nunca se registran. |
| `STORAGE_REGION` / `STORAGE_CREATE_BUCKET` | `us-east-1` / `true` | Región; crear el bucket con lectura pública si no existe. |
| `STORAGE_TIMEOUT` / `STORAGE_CONNECT_TIMEOUT` | `10s` / `2s` | Tiempo máximo por operación (TimeLimiter) y de conexión. |
| `UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE` | `5MB` / `6MB` | Tamaño máximo por foto y por request (una foto por request). |
| `SEED_ENABLED` | `false` (`.env.example`: `true`) | Carga datos de ejemplo idempotentes al arrancar. |
| `FLYWAY_ENABLED` | `true` | Aplica las migraciones al arrancar. |
| `ADDRESS_PROVIDER` | `stub` | `stub` (catálogo local, sin red) o `external` (API Georef). |
| `ADDRESS_PROVIDER_URL` / `ADDRESS_PROVIDER_API_KEY` | `https://apis.datos.gob.ar/georef/api` / — | Proveedor externo; la API key (opcional) va como `Authorization: Bearer` y nunca se registra. |
| `ADDRESS_PROVIDER_TIMEOUT_MS` / `ADDRESS_PROVIDER_MAX_RESULTS` | `2000` / `5` | Timeout por intento y máximo de sugerencias. |
| `R4J_CB_*`, `R4J_RETRY_*`, `ADDRESS_RETRY_MAX_ATTEMPTS` | ver `.env.example` | Ventana, umbral y espera del circuit breaker; reintentos y backoff. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes permitidos (coma). En Docker se usa proxy (mismo origen). |
| `ACTUATOR_USERNAME` / `ACTUATOR_PASSWORD` | `actuator` / — (requerida en Docker) | HTTP Basic para `/actuator/metrics` y `/actuator/prometheus`. |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | Muestreo de trazas (traceId/spanId siempre se generan para logs). |
| `LOG_LEVEL` / `LOG_LEVEL_APP` | `INFO` | Nivel root / paquete `com.lebane`. |
| `LOG_FORMAT` | `json` | `json` (producción/Docker) o `text` (desarrollo). |
| `LOG_SERVICE_NAME` / `LOG_ENVIRONMENT` | `lebane-backend` / `local` | Campos `service` / `environment` en cada log. |
| `LOGSTASH_ENABLED` / `LOGSTASH_HOST` / `LOGSTASH_PORT` | `false` / `logstash` / `5000` | Envío opcional a Logstash. |
| `LOGS_RETENTION_DAYS` | `7` | Retención de los índices de logs en Elasticsearch (ILM). |
| `VITE_API_BASE_URL` | `/api` | Base de la API vista por el navegador (build time, no secretos). |
| `BACKEND_UPSTREAM` | `http://backend:8080` | Upstream de nginx (red interna Docker). |
| `NGINX_RESOLVER` | `127.0.0.11` | DNS que usa nginx para re-resolver el upstream (DNS embebido de Docker). |
| `IMAGES_ORIGIN` (frontend) | `STORAGE_PUBLIC_URL` | Origen de las fotos permitido en la `Content-Security-Policy`; Compose lo deriva de `STORAGE_PUBLIC_URL`. |
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
| Backend | http://127.0.0.1:8080 | API + Actuator. Solo localhost |
| MinIO API | http://127.0.0.1:9000 | URLs públicas de imágenes (bucket `lebane-images`, lectura anónima). Solo localhost |
| MinIO consola | http://127.0.0.1:9001 | Solo localhost |
| PostgreSQL | 127.0.0.1:5432 | Solo localhost |

Healthchecks: PostgreSQL (`pg_isready`), MinIO (`mc ready local`), backend (`wget` a
`/actuator/health/readiness`, la imagen Alpine de Temurin incluye `wget` de busybox) y frontend (`wget` a `/`).
`backend` espera a `postgres` y `minio` sanos; `frontend` espera a `backend` sano.

Logs de contenedores: driver `json-file` con rotación (`max-size: 10m`, `max-file: 5`).

**Puertos**: solo el frontend escucha en todas las interfaces; backend, MinIO, PostgreSQL y el perfil de
observabilidad se publican únicamente en `127.0.0.1`. Así el backend no queda accesible desde la red sin pasar por
nginx. Para servir la aplicación a otras máquinas hay que publicar MinIO (o un CDN) y ajustar `STORAGE_PUBLIC_URL`.

## Ejecución sin Docker

```bash
# Infraestructura mínima
docker compose up -d postgres minio

# Backend (otra terminal)
cd backend
export DB_URL=jdbc:postgresql://localhost:5432/lebane DB_USERNAME=lebane DB_PASSWORD=<tu-password>
export ACTUATOR_PASSWORD=<algo> LOG_FORMAT=text
./mvnw spring-boot:run

# Frontend (otra terminal) — vite redirige /api y /actuator/health a localhost:8080
cd frontend
npm ci
npm run dev        # http://localhost:5173
```

En Windows (PowerShell): `$env:DB_PASSWORD="..."` en lugar de `export`.

## Perfil de observabilidad

```bash
# En .env: LOGSTASH_ENABLED=true (sin esto ELK levanta pero no recibe logs)
docker compose --profile observability up -d --build
```

| Servicio | URL | Credenciales |
|---|---|---|
| Elasticsearch | http://127.0.0.1:9200 | `elastic` / `ELASTIC_PASSWORD` |
| Kibana | http://127.0.0.1:5601 → *Discover* → data view **Lebane logs** | `elastic` / `ELASTIC_PASSWORD` |
| Logstash TCP | 127.0.0.1:5000 | entrada `json_lines` |

Configuración automática e idempotente (contenedores efímeros, scripts versionados en `observability/`):

| Servicio | Qué hace |
|---|---|
| `elasticsearch-setup` | Contraseña de `kibana_system`; política ILM **`lebane-logs`** (borra índices con más de `LOGS_RETENTION_DAYS` días, default 7); plantilla de índice **`lebane-logs`** |
| `kibana-setup` | Data view **Lebane logs** (`lebane-logs-*`, tiempo `@timestamp`) |

La plantilla define el mapeo de los índices diarios `lebane-logs-YYYY.MM.dd`: identificadores y dimensiones como
`keyword` (`requestId`, `traceId`, `level`, `logger`, `circuitBreaker`, `provider`, `errorCode`, …), números
(`status`, `durationMs`, `attempts`, `sizeBytes`, …), `message` como texto con subcampo `message.keyword` para
agrupar por evento, y `index.mapping.ignore_malformed`: un valor con tipo inesperado se ignora en lugar de que
Elasticsearch **rechace el evento completo** (con mapeo dinámico, un campo que llegara primero como número y luego
como texto haría perder logs).

Consultas útiles en Kibana (KQL):

| Necesidad | Consulta |
|---|---|
| Todo lo que pasó en un request (el `requestId` que ve el usuario en el error) | `requestId : "f707a231f5cb3c0ad906aa679ce9720b"` |
| Un flujo distribuido | `traceId : "6abecdf6…"` |
| Degradaciones de una dependencia | `circuitBreaker : "storage" and level : "WARN"` |
| Aperturas de circuito | `message.keyword : "Circuit breaker opened: dependency degraded"` |
| Errores 5xx | `logger : "com.lebane.access" and status >= 500` |
| Requests lentos | `logger : "com.lebane.access" and durationMs > 1000` |
| Fallbacks de direcciones | `fallback : true` |
| Objetos huérfanos a limpiar | `message : "orphan" or message : "huérfano"` (campo `objectKey`) |

- Volúmenes persistentes: `elasticsearch-data`, `logstash-data`, `kibana-data`.
- El backend **no depende** de estos servicios: no están en `depends_on` ni en readiness. Con Logstash caído, el
  appender asíncrono retiene los eventos en su buffer en memoria (8.192) y los envía al reconectar; si el buffer se
  llena, descarta los nuevos en lugar de esperar (nunca bloquea requests: 20.000 eventos en < 3 s sin destino,
  probado). Reintenta conectar cada 10 s, avisa una vez con un evento JSON (`Logback internal status`) y, al apagar, espera
  como máximo 5 s para vaciar su buffer (el default de la librería es 1 minuto). stdout siempre recibe todo.
- Con `LOGSTASH_ENABLED=false` el appender TCP **no se crea**: ninguna conexión ni evento hacia Logstash, aunque
  esté levantado.
- **Recolección y rotación**: la salida principal es stdout (JSON por línea), lista para cualquier recolector
  (Docker, Kubernetes, Fluent Bit, Filebeat). En Docker Compose cada servicio usa el driver `json-file` con
  rotación (`max-size: 10m`, `max-file: 5`). En Elasticsearch, la retención la aplica la política ILM.

## Seed de datos

Con `SEED_ENABLED=true` (valor de `.env.example`; el default de la aplicación es `false`), al arrancar se cargan 12
departamentos ficticios en distintas ciudades y estados, con consultas de ejemplo.

- **Idempotente**: cada departamento tiene un código fijo (`SEED-0001`…); si ya existe no se toca. Reiniciar no
  duplica datos ni pisa ediciones manuales.
- Cada departamento y sus consultas se insertan en una misma transacción.
- Seguro con varias instancias arrancando a la vez: el `UNIQUE(codigo)` rechaza el duplicado y esa instancia lo
  omite.
- No depende de servicios externos reales. Fotos: se generan en memoria (PNG sintéticos) y se suben con el mismo
  servicio que la API (0 a 3 por departamento; los que no tienen muestran el placeholder). Solo a departamentos
  `SEED-` sin fotos; si MinIO no está disponible se omiten (WARN) y se completan en el próximo arranque.
- Los datos pasan las mismas validaciones que la API (`SeedDataTest`).
