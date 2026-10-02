# Lebane

Aplicación inmobiliaria para gestionar **departamentos en venta**: API REST (Spring Boot 3 / Java 21 /
PostgreSQL / MinIO) y panel de administración (React + TypeScript + Vite), orquestados con Docker Compose y con
un perfil opcional de observabilidad (Elasticsearch + Logstash + Kibana).

> **Estado:** Fases 1 a 7 implementadas **y verificadas**. Fase 1: infraestructura, Actuator, health probes,
> logging JSON, correlation ID, Docker Compose y perfil ELK. Fase 2: modelo de datos, migraciones Flyway, API de
> alta / detalle / edición / consultas, validaciones, manejo global de errores y seed idempotente. Fase 3: listado
> paginado con Specifications y Criteria API, agregados en PostgreSQL, índices y validación sin full scans ni N+1
> con 100k departamentos. Fase 4: fotos en MinIO, autocompletado de direcciones (stub / API Georef) y Resilience4j
> con métricas y logs. Fase 4.1: validación de logging y observabilidad (ELK con plantilla, retención y data view
> automáticos; correlación requestId/traceId de punta a punta). Fase 5: frontend completo (listado, alta, detalle,
> edición, fotos, autocompletado y consultas). Fase 6: suite completa con cobertura medida (JaCoCo y v8) y E2E con
> Playwright contra el stack real. Fase 7: validación final de punta a punta (ver [Validación final](#validación-final)).
> Ver [Estado por fase](#estado-por-fase) y [Limitaciones conocidas](#limitaciones-conocidas).

---

## Tabla de contenidos

- [Arquitectura](#arquitectura)
- [Modelo de datos](#modelo-de-datos)
- [Requisitos](#requisitos)
- [Instalación local](#instalación-local)
- [Variables de entorno](#variables-de-entorno)
- [Ejecución con Docker Compose](#ejecución-con-docker-compose)
- [Ejecución sin Docker](#ejecución-sin-docker)
- [Perfil de observabilidad](#perfil-de-observabilidad)
- [Endpoints](#endpoints)
- [Errores](#errores)
- [Seed de datos](#seed-de-datos)
- [Listado: paginación, filtros y orden](#listado-paginación-filtros-y-orden)
- [Validación de performance](#validación-de-performance)
- [Imágenes y MinIO](#imágenes-y-minio)
- [Autocompletado de direcciones](#autocompletado-de-direcciones)
- [Resiliencia (Resilience4j)](#resiliencia-resilience4j)
- [Frontend](#frontend)
- [Actuator, liveness y readiness](#actuator-liveness-y-readiness)
- [Logging estructurado](#logging-estructurado)
- [Correlation ID](#correlation-id)
- [Política de datos sensibles](#política-de-datos-sensibles)
- [Tests](#tests)
- [Validación final](#validación-final)
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
│       ├── config          Seguridad, CORS, auditoría JPA, propiedades
│       ├── departamento    controller · dto (+ validation) · entity · mapper · repository · service
│       │                   repository: Specifications + fragmento Criteria/SQL del listado
│       ├── address         client (Georef) · controller · dto · provider (Stub/External) · service
│       ├── storage         client (MinIO) · config · service (resiliencia, métricas, logs, URLs)
│       ├── resilience      ResilientExecutor (Retry+CircuitBreaker+TimeLimiter) y logs de eventos
│       ├── logging         filter (X-Request-Id + access log) · interceptor (propagación saliente)
│       ├── exception       ApiError, ErrorCode, GlobalExceptionHandler, ApiErrorController (/error)
│       └── seed            Seed idempotente (SEED_ENABLED)
│   └── src/main/resources/db/migration   Migraciones Flyway (V1 esquema, V2 índices del listado)
├── frontend/               React 19 · TypeScript · Vite · TanStack Query · RHF · Zod · React Router
├── observability/logstash/ Pipeline de Logstash
├── docker-compose.yml
├── .env.example
└── README.md
```

Backend organizado **por dominio** (cada dominio con sus capas). Los controllers nunca exponen entidades JPA.

## Modelo de datos

```
departamento 1 ──── 0..5 imagen      (FK imagen.departamento_id, @ManyToOne LAZY)
departamento 1 ──── 0..N consulta    (FK consulta.departamento_id, @ManyToOne LAZY)
```

| Tabla | Contenido | Integridad en la base |
|---|---|---|
| `departamento` | código comercial único (`DEP-XXXXXXXX`), título, descripción, precio + moneda (`ARS`/`USD`), ambientes, dormitorios, baños, superficie, estado (`DISPONIBLE`/`RESERVADO`/`VENDIDO`), dirección embebida (calle, número, piso, unidad, ciudad, provincia, CP, lat/long, placeId), `version`, `created_at`, `updated_at` | `UNIQUE(codigo)`; `CHECK` de precio > 0, rangos, `dormitorios < ambientes`, enums, coordenadas completas y en rango |
| `imagen` | metadatos de la foto (`object_key`, `content_type`, `size_bytes`, `posicion`); el binario vive en MinIO | `UNIQUE(object_key)`; `posicion` 0..4 + `UNIQUE(departamento_id, posicion)` ⇒ **máximo 5 fotos garantizado por la base** |
| `consulta` | nombre, email, teléfono, mensaje, `created_at` (datos personales: nunca en logs ni respuestas) | FK + índice `ix_consulta_departamento` |

- **Esquema versionado con Flyway** (`db/migration`); Hibernate solo valida (`ddl-auto=validate`): si una entidad
  no coincide con la tabla, la aplicación no arranca.
- **Relaciones LAZY y sin colecciones mapeadas** en `Departamento`: ninguna operación puede cargar
  accidentalmente todas las imágenes o consultas (N+1, paginación en memoria). Imágenes y contadores se obtienen
  con consultas dedicadas. Un test verifica estas reglas por reflexión (`EntityMappingRulesTest`).
- **Dirección embebida** (`@Embeddable`): relación 1:1 que siempre se lee con el departamento; una tabla aparte
  solo agregaría un JOIN a cada consulta, incluido el listado filtrado por ciudad.
- **IDs por secuencia con `INCREMENT BY 50`** (optimizador *pooled* de Hibernate): inserciones sin un round-trip
  por fila. Consecuencia visible: los IDs no son consecutivos entre reinicios (cada JVM reserva bloques de 50).
- **Auditoría** con Spring Data JPA (`@CreatedDate`, `@LastModifiedDate`) y **concurrencia optimista** con
  `@Version`.
- Índices del listado (`V2__indices_listado.sql`): uno por orden admitido, con el desempate por `id` al final
  (`(created_at, id)`, `(estado, created_at, id)`, `(lower(ciudad), created_at, id)`, `(moneda, precio, id)`,
  `(superficie_m2, id)`) y un GIN de trigramas sobre `lower(titulo)` para la búsqueda de texto. Ver
  [Validación de performance](#validación-de-performance).

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

## Endpoints

API REST versionada bajo `/api/v1`. JSON en request y response. En Docker el navegador la consume vía el proxy del
frontend (`http://localhost:3000/api/...`, mismo origen); directo en `http://localhost:8080/api/...`.

**Documentación OpenAPI 3.1** (springdoc, generada desde el código):

- Swagger UI para explorar y probar la API: http://localhost:8080/swagger-ui.html
- Spec en JSON: http://localhost:8080/v3/api-docs; versionado en [`docs/openapi.json`](docs/openapi.json)
- Cada operación documenta parámetros, headers (`X-Request-Id`, `ETag`, `If-Match`, `Location`) y solo las
  respuestas que realmente puede devolver. Los errores usan el esquema `ApiError`, con ejemplos que traen los
  mensajes reales.
- `OpenApiSpecTest` falla si la API cambia y `docs/openapi.json` no, o si una operación, un parámetro o una
  propiedad queda sin documentar. Para regenerar el spec:
  `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true`.
- `OPENAPI_ENABLED=false` apaga el spec y Swagger UI (por ejemplo, en producción si no deben ser públicos).

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/v1/departamentos` | Listado paginado con filtros y orden ([detalle](#listado-paginación-filtros-y-orden)) | `200` · `400` |
| `POST` | `/api/v1/departamentos` | Alta | `201` + `Location` + `ETag` · `400` · `409` |
| `GET` | `/api/v1/departamentos/{id}` | Detalle completo (dirección, imágenes ordenadas, cantidad de consultas) | `200` + `ETag` · `400` · `404` |
| `PUT` | `/api/v1/departamentos/{id}` | Edición (reemplazo completo); `If-Match` opcional | `200` + `ETag` · `400` · `404` · `409` · `412` |
| `POST` | `/api/v1/departamentos/{id}/consultas` | Registrar una consulta de un interesado | `201` · `400` · `404` · `409` (vendido) |
| `POST` | `/api/v1/departamentos/{id}/imagenes` | Subir una foto (`multipart/form-data`, campo `archivo`) ([detalle](#imágenes-y-minio)) | `201` · `400` · `404` · `409` · `413` · `503` |
| `DELETE` | `/api/v1/departamentos/{id}/imagenes/{imagenId}` | Eliminar una foto | `204` · `404` |
| `GET` | `/api/v1/direcciones/autocompletar?q=` | Autocompletado de direcciones ([detalle](#autocompletado-de-direcciones)) | `200` · `400` |

**Alta / edición** (`DepartamentoRequest`):

```bash
curl -i -X POST http://localhost:8080/api/v1/departamentos \
  -H 'Content-Type: application/json' -H 'X-Request-Id: demo-001' \
  -d '{
    "titulo": "3 ambientes con balcón en Palermo",
    "descripcion": "Luminoso, piso alto.",
    "precio": 185000, "moneda": "USD",
    "ambientes": 3, "dormitorios": 2, "banos": 1, "superficieM2": 72.5,
    "estado": "DISPONIBLE",
    "direccion": {
      "calle": "Gorriti", "numero": "4850", "piso": "7", "unidad": "B",
      "ciudad": "Ciudad Autónoma de Buenos Aires", "provincia": "CABA", "codigoPostal": "C1414",
      "latitud": -34.5889, "longitud": -58.4305
    }
  }'
# HTTP/1.1 201
# Location: /api/v1/departamentos/1
# ETag: "0"
```

| Campo | Reglas |
|---|---|
| `titulo` | obligatorio, ≤ 120 |
| `descripcion` | opcional, ≤ 4000 |
| `precio` | obligatorio, > 0, hasta 12 enteros y 2 decimales |
| `moneda` | `ARS` \| `USD` |
| `ambientes` | 1..20 |
| `dormitorios` | 0..19 y **menor que `ambientes`** (un monoambiente tiene 0) |
| `banos` | 1..10 |
| `superficieM2` | > 0, hasta 6 enteros y 2 decimales |
| `estado` | opcional: en el alta, `DISPONIBLE`; en la edición, si se omite se conserva |
| `direccion.calle` / `numero` / `ciudad` / `provincia` | obligatorios (≤ 120 / 10 / 80 / 80) |
| `direccion.piso` / `unidad` / `codigoPostal` / `placeId` | opcionales |
| `direccion.latitud` / `longitud` | opcionales, **juntas o ninguna**, en rango (±90 / ±180) |

Los textos se normalizan (espacios recortados; opcionales vacíos ⇒ `null`). El `codigo` (`DEP-XXXXXXXX`) lo
genera el backend y no se puede modificar.

**Concurrencia optimista**: el detalle devuelve `ETag: "<version>"`. Si la edición envía `If-Match` con ese valor
y otro usuario modificó el departamento desde entonces, responde `412 PRECONDITION_FAILED` sin cambios. Sin
`If-Match`, las escrituras concurrentes que chocan en la base responden `409 CONCURRENT_MODIFICATION`.

```bash
curl -i -X PUT http://localhost:8080/api/v1/departamentos/1 \
  -H 'Content-Type: application/json' -H 'If-Match: "0"' -d @departamento.json
```

**Consultas** (`ConsultaRequest`): `nombre` (obligatorio, ≤ 100), `email` (obligatorio, válido), `telefono`
(opcional, `+`, dígitos, espacios, guiones y paréntesis, 6..30), `mensaje` (10..2000). Un departamento `VENDIDO`
no recibe consultas (`409 DEPARTAMENTO_NO_DISPONIBLE`). La respuesta solo incluye `id`, `departamentoId` y
`createdAt`: nunca devuelve los datos personales.

```bash
curl -i -X POST http://localhost:8080/api/v1/departamentos/1/consultas \
  -H 'Content-Type: application/json' \
  -d '{"nombre":"Ana Pérez","email":"ana@example.com","mensaje":"¿Se puede visitar el sábado?"}'
```

**Detalle** (`DepartamentoDetailResponse`): todos los campos anteriores más `id`, `codigo`, `imagenes`
(`id`, `url`, `contentType`, `sizeBytes`, `posicion`; ordenadas, la primera es la principal), `cantidadConsultas`,
`version`, `createdAt`, `updatedAt`. Se arma con **3 sentencias SQL fijas** (departamento por PK, imágenes por FK y
`COUNT` de consultas), con 0 o 5 fotos: lo verifica `DepartamentoApiIT` con las estadísticas de Hibernate.

## Errores

Todas las respuestas de error (validación, dominio, Spring MVC, Spring Security y errores fuera de MVC vía
`/error`) usan el mismo esquema `ApiError`:

```json
{
  "timestamp": "2026-10-01T12:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "La solicitud contiene datos inválidos",
  "path": "/api/v1/departamentos",
  "requestId": "demo-001",
  "fieldErrors": {
    "precio": "debe ser mayor que 0",
    "dormitorios": "debe ser menor que la cantidad de ambientes",
    "direccion.ciudad": "no debe estar vacío",
    "moneda": "valor inválido; valores permitidos: ARS, USD"
  }
}
```

| `error` | HTTP | Cuándo |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation, tipos o enums inválidos, parámetros faltantes. `fieldErrors` por ruta de campo |
| `BAD_REQUEST` | 400 | JSON mal formado, header obligatorio ausente |
| `UNAUTHORIZED` / `FORBIDDEN` | 401 / 403 | Endpoints protegidos de Actuator |
| `NOT_FOUND` | 404 | Recurso o ruta inexistente |
| `METHOD_NOT_ALLOWED` | 405 | Con header `Allow` |
| `NOT_ACCEPTABLE` | 406 | El cliente no acepta JSON (sin cuerpo) |
| `CONFLICT` | 409 | Violación de una restricción de la base (sin exponer SQL ni nombres de constraints) |
| `CONCURRENT_MODIFICATION` | 409 | Conflicto de concurrencia optimista |
| `DEPARTAMENTO_NO_DISPONIBLE` | 409 | Regla de negocio (consulta sobre un departamento vendido) |
| `LIMITE_IMAGENES_ALCANZADO` | 409 | El departamento ya tiene 5 fotos |
| `STORAGE_UNAVAILABLE` | 503 | MinIO caído, lento, con el circuito abierto o mal configurado (detalle técnico solo en logs) |
| `PRECONDITION_FAILED` | 412 | `If-Match` desactualizado |
| `PAYLOAD_TOO_LARGE` | 413 | Request mayor al límite |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content-Type distinto de JSON |
| `SERVICE_UNAVAILABLE` | 503 | Base de datos no disponible |
| `INTERNAL_ERROR` | 500 | Inesperado: mensaje genérico + `requestId` para soporte |

Nunca se devuelven stack traces, SQL, nombres de clases, hosts ni mensajes internos de Jackson o Hibernate. Los
mensajes de validación están en español independientemente del `Accept-Language` (`spring.web.locale=es`). Cada
excepción se registra una sola vez, en el handler: 4xx en `DEBUG`, 503 en `WARN` y 500 en `ERROR` con stack trace,
siempre con `errorCode` y `status` como campos estructurados.

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

## Listado: paginación, filtros y orden

`GET /api/v1/departamentos` — todo se resuelve en PostgreSQL (filtros, orden, `OFFSET`/`LIMIT`, totales y
agregados); el cliente nunca recibe más que una página.

| Parámetro | Ejemplo | Regla |
|---|---|---|
| `q` | `q=balcón` | Texto contenido en el título, sin distinguir mayúsculas. 3 a 100 caracteres. `%` y `_` se buscan literalmente |
| `ciudad` | `ciudad=rosario` | Ciudad exacta, sin distinguir mayúsculas |
| `estado` | `estado=DISPONIBLE&estado=RESERVADO` o `estado=DISPONIBLE,RESERVADO` | Uno o más de `DISPONIBLE`, `RESERVADO`, `VENDIDO` |
| `moneda` | `moneda=USD` | `ARS` o `USD`. **Obligatoria si se filtra por precio** (no se comparan montos de distintas monedas) |
| `precioMin` / `precioMax` | `precioMin=100000&precioMax=200000` | ≥ 0, mínimo ≤ máximo |
| `superficieMin` / `superficieMax` | `superficieMin=40` | m², ≥ 0, mínimo ≤ máximo |
| `ambientesMin` / `dormitoriosMin` / `banosMin` | `ambientesMin=3` | Mínimos |
| `conImagenes` | `conImagenes=true` | `true`: solo con fotos; `false`: solo sin fotos |
| `page` | `page=0` | Desde 0. Default 0 |
| `size` | `size=20` | 1..100. Default 20 |
| `sort` | `sort=precio,asc` | `createdAt` (default, `desc`), `precio` (dentro de cada moneda), `superficieM2`; con `,asc` o `,desc` |

Ventana máxima: `(page + 1) × size ≤ 10.000`. Con `OFFSET`, PostgreSQL recorre y descarta las filas anteriores; más
allá de esa profundidad se pide refinar los filtros (`400` en `page`).

```bash
curl 'http://localhost:8080/api/v1/departamentos?estado=DISPONIBLE&moneda=USD&precioMax=200000&sort=precio,asc&size=2'
```

```json
{
  "content": [
    {
      "id": 7, "codigo": "SEED-0007", "titulo": "2 ambientes reciclado en San Telmo",
      "precio": 89000.0, "moneda": "USD", "ambientes": 2, "dormitorios": 1, "banos": 1, "superficieM2": 45.0,
      "estado": "DISPONIBLE", "ciudad": "Ciudad Autónoma de Buenos Aires", "provincia": "CABA",
      "imagenPrincipalUrl": null, "cantidadImagenes": 0, "cantidadConsultas": 0,
      "createdAt": "2026-10-01T19:32:32.839995Z"
    }
  ],
  "page": {"size": 2, "number": 0, "totalElements": 7, "totalPages": 4}
}
```

(Respuesta real con el seed; `content` recortado al primer ítem.) Cada ítem trae solo lo que muestra la tarjeta del listado (sin descripción, dirección completa ni lista de fotos).
`imagenPrincipalUrl` es la foto de menor posición, o `null` si no tiene (el frontend muestra un placeholder). El
formato de página es el estándar de Spring Data (`PagedModel`).

### Cómo se ejecuta (3 consultas fijas por página)

```
1. Página    Specification + Criteria API, proyección directa a DTO (select new ...):
             SELECT id, codigo, titulo, precio, ... FROM departamento WHERE <filtros>
             ORDER BY <índice> OFFSET ? FETCH FIRST ? ROWS ONLY
2. Total     JpaSpecificationExecutor.count(spec): mismos predicados, sin JOINs.
             Se omite cuando el total se deduce de la página (p. ej. primera página incompleta).
3. Agregados Criteria API, solo para los IDs de la página, en una consulta:
             SELECT d.id, (SELECT COUNT(...) FROM imagen ...), (foto de menor posición),
                    (SELECT COUNT(...) FROM consulta ...) FROM departamento d WHERE d.id IN (...)
             (subconsultas escalares correlacionadas, cada una resuelta con un índice)
```

**Sin consultas escritas como texto**: página, filtros, orden, total y agregados se construyen con la Criteria API y
el metamodelo estático (`Departamento_`, `Imagen_`, ...). Ni `@Query` con JPQL/SQL, ni `createQuery(String)`, ni
SQL nativo, ni concatenación de cadenas; los nombres de atributos tampoco van en texto. `ListadoSinConsultasDeTextoTest`
hace fallar el build si el código del listado vuelve a tener una consulta de texto.

- **Sin full scans**: cada filtro y orden usa un índice (ver [Validación de performance](#validación-de-performance)).
- **Sin N+1**: la cantidad de consultas no depende del tamaño de página ni de las fotos o consultas
  (`ListadoIT`, `ListadoPerformanceIT`). No se cargan entidades (`entityLoadCount = 0`).
- **Sin multiplicación de filas**: los filtros nunca hacen JOIN a colecciones (`conImagenes` usa `EXISTS`), así que
  el `COUNT` es directo. Los agregados son subconsultas por departamento, sin JOIN entre `imagen` y `consulta`:
  no hay producto fotos × consultas y los conteos son exactos sin `COUNT(DISTINCT ...)`.
- **Agregados con subconsultas correlacionadas**: la Criteria API estándar no admite tablas derivadas en el
  `FROM`; las subconsultas escalares por fila sí, y con los índices `uk_imagen_departamento_posicion` e
  `ix_consulta_departamento` cada una es un *index-only scan* sobre las filas de la página (≤ 100). Se cuentan
  `posicion` y `departamento_id` (columnas `NOT NULL` del índice) en lugar de `id`, que obligaría a leer cada fila
  de la tabla.
- **Orden por lista blanca** (`CampoOrden`): ningún nombre de propiedad enviado por el cliente llega a la consulta.
  Siempre termina en `id` para que la paginación sea determinística con valores repetidos.

## Validación de performance

Medido con **100.000 departamentos, ~200.000 imágenes y ~300.000 consultas** (`perf/datos-volumen.sql`), después
de `VACUUM ANALYZE`.

**Automático** — `ListadoPerformanceIT` (en `./mvnw verify`, con un PostgreSQL propio): para 11 escenarios
(orden por defecto, página profunda, estado, ciudad, rango de precio, orden por precio y superficie, búsqueda de
texto, filtros combinados) verifica con los contadores de PostgreSQL (`pg_stat_user_tables`) que **ninguna**
consulta hizo *sequential scan* sobre `departamento`, `imagen` ni `consulta`, y que se ejecutan ≤ 3 sentencias. Un
control negativo comprueba que la medición sí detecta un seq scan real.

**`EXPLAIN (ANALYZE, BUFFERS)`** — `perf/explain-listado.sql`, sobre la misma volumetría:

| Consulta | Plan | Tiempo |
|---|---|---|
| Página por defecto (`created_at DESC`) | `Index Scan Backward` en `ix_departamento_created` + `Limit` | 0,2 ms |
| Página profunda (offset 9.900, 100 filas) | `Index Scan Backward` en `ix_departamento_created` (lee 10.000 entradas) | 3,2 ms |
| `estado = VENDIDO` (página) | `Index Scan Backward` en `ix_departamento_estado_created` | 0,08 ms |
| `estado = VENDIDO` (`COUNT`) | `Index Only Scan` en `ix_departamento_estado_created`, `Heap Fetches: 0` | 4,3 ms |
| `ciudad = rosario` (página) | `Index Scan Backward` en `ix_departamento_created` + filtro (1 de cada 8 coincide) | 0,1 ms |
| `ciudad = rosario` (`COUNT`) | `Bitmap Index Scan` en `ix_departamento_ciudad_created` | 8,5 ms |
| USD entre 100k y 200k, orden por precio | `Index Scan` en `ix_departamento_moneda_precio` (rango en el índice) | 0,1 ms |
| `q = balcón` (página) | `Index Scan Backward` en `ix_departamento_created` + filtro | 0,1 ms |
| `q = reciclado` + ciudad (`COUNT`) | `Bitmap Index Scan` en `ix_departamento_titulo_trgm` (trigramas) | 22,6 ms |
| `COUNT` sin filtros | `Index Only Scan` en `pk_departamento`, `Heap Fetches: 0` | 15,9 ms |
| Agregados de 20 IDs | Subconsultas correlacionadas: `Index Only Scan` en `uk_imagen_departamento_posicion` e `ix_consulta_departamento` (`Heap Fetches: 0`), `Index Scan` para la foto principal | 0,6 ms |

Ningún plan contiene `Seq Scan`. Cuando el filtro es poco selectivo, PostgreSQL elige recorrer el índice del orden
y filtrar hasta completar la página (más barato que usar el índice del filtro y ordenar). Un `COUNT` exacto debe
contar todas las filas que cumplen el filtro: sin filtros es O(n) sobre el índice más chico (16 ms con 100k filas).
Por eso se omite cuando la página permite deducir el total.

Reproducir sobre una base descartable, sin tocar la de desarrollo:

```bash
P="docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U lebane"
$P -d lebane -c "CREATE DATABASE lebane_perf"
$P -d lebane_perf < backend/src/main/resources/db/migration/V1__esquema_inicial.sql
$P -d lebane_perf < backend/src/main/resources/db/migration/V2__indices_listado.sql
$P -d lebane_perf < backend/src/test/resources/perf/datos-volumen.sql
$P -d lebane_perf -c "VACUUM ANALYZE departamento, imagen, consulta"
$P -d lebane_perf < backend/src/test/resources/perf/explain-listado.sql
$P -d lebane -c "DROP DATABASE lebane_perf"
```

## Imágenes y MinIO

```bash
# Subir (una foto por request; el tipo se detecta por el contenido, no por el nombre)
curl -i -F "archivo=@casa.jpg" http://localhost:8080/api/v1/departamentos/1/imagenes
# HTTP/1.1 201
# {"id":19,"url":"http://localhost:9000/lebane-images/departamentos/1/80d7…png","contentType":"image/png",
#  "sizeBytes":8600,"posicion":0}

curl -i -X DELETE http://localhost:8080/api/v1/departamentos/1/imagenes/19      # 204
```

| Regla | Detalle |
|---|---|
| Formatos | JPEG, PNG, WebP, detectados por **magic bytes**. Un HTML renombrado a `.jpg` → `400 fieldErrors.archivo` |
| Tamaño | `UPLOAD_MAX_FILE_SIZE` (5 MB): `413` |
| Máximo | 5 fotos por departamento → `409 LIMITE_IMAGENES_ALCANZADO`. Lo garantiza la base (`posicion` 0..4 única), también con subidas concurrentes |
| Principal | La de menor `posicion` (al subir se ocupa la primera libre) |
| Claves | `departamentos/{id}/{uuid}.{ext}`, generadas por el backend: sin nombre del usuario (sin path traversal ni colisiones) |
| URLs | Bucket con **lectura pública** de objetos (`s3:GetObject`); listar, escribir y borrar requieren credenciales. Las fotos de un aviso inmobiliario son públicas por naturaleza; si no lo fueran, `ImageUrlResolver` permite cambiar a URLs firmadas sin tocar el dominio |

**Flujo de subida** (`ImagenService`):

1. Validación barata antes de tocar la red: el departamento existe, tamaño, contenido real y prechequeo del
   límite.
2. Subida a MinIO **fuera de toda transacción**: no se mantiene una conexión ni un lock de base abiertos mientras
   se transfiere un archivo por red.
3. Registro en una transacción corta con la fila del departamento bloqueada (`SELECT … FOR UPDATE`): se vuelve a
   verificar el límite y se asigna la posición.
4. Si el registro falla (límite alcanzado por una subida concurrente, error de base), **borrado compensatorio** del
   objeto. Si también falla, se registra en ERROR con su `objectKey` (objeto huérfano a limpiar).

**Eliminación**: primero la fila (la foto desaparece de inmediato), después el objeto. Si MinIO falla en ese paso
la respuesta sigue siendo `204` y se registra el objeto huérfano: nunca queda una referencia a una foto inexistente.

**MinIO no está en el readiness**: listado, detalle, alta y edición no lo usan (el navegador descarga las fotos
directo de MinIO). Si MinIO cae, solo fallan las subidas y bajas de fotos (`503 STORAGE_UNAVAILABLE`); la
aplicación sigue atendiendo y no se reinicia. El bucket se prepara al arrancar en segundo plano o, si MinIO no
estaba disponible, antes de la primera subida.

**Logs de storage** (sin credenciales, URLs firmadas ni contenido): `Storage upload started` / `completed`
(`bucket`, `objectKey`, `sizeBytes`, `contentType`, `durationMs`), `Storage operation failed` (WARN si es
transitorio, ERROR si es de configuración), `Storage compensating delete executed` / `failed: orphan object`.

## Autocompletado de direcciones

```bash
curl 'http://localhost:8080/api/v1/direcciones/autocompletar?q=Av%20Santa%20Fe%201860&limite=2'
```

```json
{
  "sugerencias": [
    { "calle": "Av Santa Fe", "numero": "1860", "ciudad": "Ciudad Autónoma de Buenos Aires",
      "provincia": "Ciudad Autónoma de Buenos Aires", "latitud": -34.5958, "longitud": -58.3941,
      "placeId": "georef:…:1860", "descripcion": "AV SANTA FE 1860, Comuna 2, Ciudad Autónoma de Buenos Aires" }
  ],
  "proveedor": "georef",
  "degradado": false
}
```

- **Abstracción** `AddressProvider` con dos implementaciones, elegidas por `ADDRESS_PROVIDER`:
  - `StubAddressProvider` (`stub`, default): catálogo local de calles argentinas, sin red, sin acentos ni
    mayúsculas. Devuelve la altura tipeada y **no inventa coordenadas** (`null`). Para desarrollo y tests.
  - `ExternalAddressProvider` (`external`): [API Georef](https://datosgobar.github.io/georef-ar-api/) del Estado
    argentino (normalización de direcciones, gratuita, sin API key). Los tipos de Georef no salen de
    `address.client`; otro proveedor es otra implementación de la interfaz.
- **Fallback útil y honesto**: el autocompletado es una ayuda; el formulario admite cargar la dirección a mano. Si
  el proveedor falla, tarda o el circuito está abierto, la respuesta es `200` con `degradado: true`, sin
  sugerencias y con un `mensaje` para el usuario. **Nunca** se devuelven datos de otro origen como si fueran del
  proveedor.
- El cliente HTTP es el `RestClient` de Spring Boot: métrica `http_client_requests_seconds` y propagación de
  `traceparent` automáticas, más `X-Request-Id`. Clasificación de errores: red, 5xx y 429 son transitorios (se
  reintentan); 400 es "sin resultados"; 401/403 y respuestas inválidas son permanentes (ERROR en logs).

## Resiliencia (Resilience4j)

Cada integración externa tiene una instancia (`storage`, `address`) que combina, en este orden:

```
Retry ( CircuitBreaker ( TimeLimiter ( llamada ) ) )      ← ResilientExecutor
```

- **TimeLimiter**: cada intento tiene su timeout y al vencer se **cancela** (la llamada corre en un virtual thread
  con el contexto del request: MDC y trace).
- **CircuitBreaker**: cuenta cada intento. Con el circuito abierto, la llamada se rechaza sin tocar la red.
- **Retry**: reintenta con backoff exponencial **solo fallos transitorios** (timeout, E/S, 5xx, throttling;
  `TransientFailurePredicate`). Los permanentes (credenciales, 4xx) no se reintentan ni abren el circuito.
- **Fallbacks** en quien llama, que conoce la semántica: storage → `503 STORAGE_UNAVAILABLE`; direcciones →
  respuesta degradada.

| Parámetro (default) | `storage` | `address` |
|---|---|---|
| Timeout por intento | `STORAGE_TIMEOUT` = 10 s | `ADDRESS_PROVIDER_TIMEOUT_MS` = 2 s |
| Intentos | `R4J_RETRY_MAX_ATTEMPTS` = 3 | `ADDRESS_RETRY_MAX_ATTEMPTS` = 2 (interactivo) |
| Backoff | 200 ms × 2 | 200 ms × 2 |
| Circuito | ventana de 20 llamadas, mínimo 5, abre con 50 % de fallos, 30 s abierto, 2 de prueba en `HALF_OPEN` | ídem |

**Eventos registrados** (logger `com.lebane.resilience`, JSON con `circuitBreaker`, `provider`, `requestId`,
`traceId` y, según el caso, `fromState`, `toState`, `attempts`, `waitMs`, `timeoutMs`, `cause`, `durationMs`; nunca
el mensaje de la excepción, que puede contener URLs o claves):

| Evento | Nivel |
|---|---|
| `Circuit breaker opened: dependency degraded` | WARN |
| `Call rejected: circuit breaker is open` | WARN |
| `Retries exhausted` | WARN |
| `Call timed out` | WARN |
| `… fallback to manual entry` / `Storage operation failed` | WARN (ERROR si es permanente) |
| `Retrying call`, `Call succeeded after retrying`, transición a `HALF_OPEN` | INFO |
| `Circuit breaker closed: dependency recovered` | INFO |

Verificado en Docker: con MinIO detenido, la primera subida agota los reintentos, la segunda abre el circuito y la
tercera se rechaza en 30 ms; readiness, listado y detalle siguen en 200. Al volver MinIO, pasados 30 s, el circuito
pasa a `HALF_OPEN` y se cierra con las primeras subidas exitosas.

## Frontend

Panel en `http://localhost:3000` (Docker) o `http://localhost:5173` (`npm run dev`).

| Pantalla | Ruta | Qué hace |
|---|---|---|
| Listado | `/departamentos` (`/` redirige) | Tarjetas con foto principal, precio, ubicación, características y contadores. Filtros (texto, ciudad, estado, moneda + precio, ambientes, con/sin fotos), orden y tamaño de página |
| Alta | `/departamentos/nuevo` | Formulario validado, autocompletado de dirección y hasta 5 fotos con vista previa |
| Detalle | `/departamentos/:id` | Galería, datos completos, mapa (si hay coordenadas) y formulario de consulta |
| Edición | `/departamentos/:id/editar` | Mismo formulario con concurrencia optimista y gestión de fotos (eliminar / subir) |

**Listado**
- **Paginación, filtros y orden en el servidor**: cada cambio es un request; el cliente nunca filtra ni pagina
  datos. La página anterior se muestra atenuada mientras llega la nueva (`keepPreviousData`).
- **La URL es la fuente de verdad** (`?q=balcón&estado=DISPONIBLE&sort=precio,desc&page=2`): se puede compartir,
  recargar y usar el botón "atrás". Los valores inválidos se descartan en lugar de provocar un 400, y la paginación
  respeta la ventana máxima del backend.
- Cambios rápidos (aplicar un filtro y cambiar el orden enseguida) se componen sobre el último estado pedido
  (`useListadoSearch`): ninguno pisa al anterior.
- Estados: cargando, vacío ("ningún resultado con estos filtros" vs "todavía no hay departamentos"), error con
  código de seguimiento y reintento.
- **Imágenes**: placeholder "Sin fotos" y, si una URL no carga (objeto borrado, MinIO caído), "Imagen no
  disponible" en lugar del ícono roto.

**Alta y edición**
- React Hook Form + Zod con **las mismas reglas que el backend** (incluidas las que cruzan campos: dormitorios <
  ambientes, coordenadas completas, moneda obligatoria para filtrar precio). Los `fieldErrors` de un 400 del
  servidor se muestran en el campo correspondiente porque los nombres coinciden (`direccion.ciudad`).
- **Autocompletado de dirección** con *debounce* (300 ms, mínimo 3 caracteres): completa calle, número, ciudad,
  provincia y coordenadas, que siguen siendo editables. Si el proveedor está degradado, avisa que se cargue a mano.
- **Fotos**: tipo validado por **contenido** (firma JPEG/PNG/WebP, igual que el backend), máximo 5 MB y 5 fotos
  (contando las ya cargadas); vista previa con object URLs liberadas al quitar o salir; quitar antes de enviar.
- **Subida con errores parciales**: el departamento se crea y las fotos se suben de a una, cada una con su estado
  (pendiente, subiendo, subida, error con código de seguimiento). Si alguna falla, el departamento ya existe: se
  ofrece **reintentar solo las fallidas**, sin volver a crearlo.
- **Edición sin pisar cambios ajenos**: se envía `If-Match` con la versión leída. Ante un 412 se avisa y se ofrece
  recargar los datos actuales.

**Transversal**
- Respuestas de la API validadas con Zod en runtime (un cambio de contrato se ve como "respuesta inválida", no
  como `undefined` en la UI).
- Cada request lleva un `X-Request-Id` (UUID); los errores muestran ese código, nunca detalles técnicos.
- Reintentos automáticos solo para errores transitorios (red, timeout, 5xx, 429); nunca para 4xx.
- Code splitting por ruta y chunks de librerías (ningún chunk supera 500 kB; la carga inicial del listado suma unos 162 kB gzip y alta, detalle y edición se descargan al navegar a ellas).
- Accesible: labels asociados, errores anunciados (`role="alert"`), paginación con `aria-current`, foco visible,
  respeta `prefers-reduced-motion`.

**Identidad visual** — alineada con [lebane.app](https://www.lebane.app/ar) (relevada del sitio: tipografías,
paleta y medidas de sus componentes), en `src/index.css` (tokens y base) y `src/styles/app.css` (pantallas):
- Plus Jakarta Sans en títulos, botones y navegación; Inter en el texto. Autoalojadas con `@fontsource-variable`
  (sin CDN: la CSP de nginx solo permite fuentes del propio origen; Vite nunca las incrusta como `data:`).
- Azul `#2065FF`, texto `#343A46`, títulos casi negros, fondos `#F7F7F7` y azul suave `#E2E8FE`.
- Barra de navegación flotante en forma de píldora, botones píldora (primario y secundario con borde azul tenue),
  tarjetas blancas de 24 px de radio con sombra difusa, etiquetas de sección en mayúsculas con punto azul, estados
  en pastillas pastel y rótulos de datos en mayúsculas chicas, como en el panel del producto.
- Solo tema claro, como el sitio (se quitó el modo oscuro anterior). La marca de la barra es un ícono propio
  simple; no se copiaron logos ni imágenes del sitio.

**Validación en el navegador contra el stack real**: alta con autocompletado y foto real (un HTML renombrado a
`.jpg` se rechazó), navegación al detalle, consulta (el contador se actualiza), filtros y orden, y un conflicto de
edición simulando a otro usuario por la API (412 → recargar → guardar conserva ambos cambios). Cada acción quedó
correlacionada en los logs del backend por su `requestId`. Esta prueba encontró dos errores que ahora cubren
tests: un filtro que se perdía al cambiar el orden enseguida y el formato de precio con centavos
("132.500,5" → "132.500,50").

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
  readiness. **MinIO tampoco**: solo lo usan las subidas y bajas de fotos (ver [Imágenes y MinIO](#imágenes-y-minio)).
  Los health indicators de los circuit breakers no se registran por el mismo motivo.

```bash
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
curl -u actuator:$ACTUATOR_PASSWORD http://localhost:8080/actuator/prometheus | head
```

Para aislar Actuator en red interna se puede definir `MANAGEMENT_SERVER_PORT` (por ejemplo `8081`) y no
publicarlo en el host (ajustar el healthcheck al nuevo puerto).

Métricas: HTTP server (`http_server_requests_seconds`, con histogramas y SLOs), HTTP client
(`http_client_requests_seconds`, llamadas al proveedor de direcciones), JVM, CPU, Hikari (`hikaricp_*`), Tomcat,
logback (eventos por nivel) y, desde la Fase 4:

| Métrica | Tags | Qué mide |
|---|---|---|
| `resilience4j_circuitbreaker_state` | `name`, `state` | Estado de cada circuito (1 = estado actual) |
| `resilience4j_circuitbreaker_calls_seconds` / `_failure_rate` / `_not_permitted_calls_total` | `name`, `kind` | Llamadas, tasa de fallos y rechazos por circuito abierto |
| `resilience4j_retry_calls_total` | `name`, `kind` | Éxitos y fallos con o sin reintento |
| `resilience4j_timelimiter_calls_total` | `name`, `kind` | Llamadas exitosas, fallidas y por timeout |
| `lebane_storage_operations_seconds` | `operation`, `outcome`, `provider` | Latencia y resultado de `upload`, `delete`, `compensatingDelete`, `ensureBucket` |
| `lebane_address_autocomplete_seconds` | `provider`, `outcome` | Latencia y resultado (`success` / `fallback`) del autocompletado |

Tags comunes: `application`, `environment`.

## Logging estructurado

| `LOG_FORMAT` | Salida | Archivo |
|---|---|---|
| `json` (default) | JSON por línea a stdout (Logstash Logback Encoder) | `logback-format-json.xml` |
| `text` | Texto legible con requestId/traceId | `logback-format-text.xml` |
| + `LOGSTASH_ENABLED=true` | Además TCP asíncrono a Logstash | `logback-logstash-true.xml` |

`logback-spring.xml` selecciona los archivos con includes parametrizados (`logback-format-${LOG_FORMAT}.xml` y, opcional,
`logback-logstash-${LOGSTASH_ENABLED}.xml`), sin condicionales `<if>`: en Logback 1.5 el atributo `condition` está
deprecado y Spring Boot imprime ese aviso en stdout como texto plano, lo que rompía el stream JSON. `LOG_FORMAT`
(`json`|`text`) y `LOGSTASH_ENABLED` (`true`|`false`, en minúsculas) se validan al arrancar (`LoggingProperties`).

Ejemplo de access log:

```json
{"@timestamp":"2026-10-01T12:00:00.123-03:00","message":"HTTP GET /api/v1/departamentos -> 200 in 12 ms",
 "logger":"com.lebane.access","thread":"tomcat-handler-3","level":"INFO",
 "requestId":"3f0c…","traceId":"6512…","spanId":"9a1b…",
 "method":"GET","path":"/api/v1/departamentos","status":200,"durationMs":12,
 "remoteAddress":"172.18.0.5","responseSize":null,
 "service":"lebane-backend","application":"lebane-backend","environment":"local"}
```

Campos: `@timestamp`, `level`, `logger`, `thread`, `message`, `service`, `application`, `environment`,
`requestId`, `traceId`, `spanId` (MDC) y campos estructurados por evento (`method`, `path`, `status`,
`durationMs`, `remoteAddress`, `responseSize`; según el evento: `errorCode`, `status`, `circuitBreaker`,
`provider`, `fromState`, `toState`, `attempts`, `cause`, `fallback`, `bucket`, `objectKey`, `sizeBytes`,
`contentType`, `departamentoId`, …).
Las excepciones van en `exception` (stack trace acortado, causa raíz primero). Nunca se concatenan campos en el
mensaje: se usan `StructuredArguments`. `responseSize` es el `Content-Length` cuando la respuesta lo tiene; las
respuestas JSON se envían *chunked* y lo dejan en `null` (medirlas exigiría envolver y contar toda la salida). Los
avisos internos de Logback llegan como eventos con `logbackOrigin` y `cause`.

Niveles: `ERROR` fallos inesperados · `WARN` degradación (CB abierto, fallback, timeout, 5xx) · `INFO` ciclo de
vida/negocio y access log · `DEBUG` detalle técnico (incluye probes de health y scraping de Prometheus).

## Correlation ID

`RequestIdFilter` (orden `HIGHEST_PRECEDENCE + 10`, después del filtro de observación de Micrometer y antes de
Spring Security):

1. Lee `X-Request-Id`; si falta o no cumple `^[A-Za-z0-9._:-]{1,128}$` (evita log injection) genera un UUID.
2. Lo devuelve en el header de respuesta, lo coloca en el MDC (`requestId`) y como atributo del request.
3. Escribe el access log al finalizar y **siempre** limpia el MDC.

`traceId`/`spanId` los aporta Micrometer Tracing (Brave, propagación W3C `traceparent`), sin exporter: están en
todos los logs de un request (también con muestreo en 0,1, porque el contexto existe aunque el span no se exporte).
Un `traceparent` entrante se **continúa** (mismo `traceId`, span nuevo). Hacia afuera, las llamadas al proveedor de
direcciones llevan `X-Request-Id` (`RequestIdPropagationInterceptor`) y `traceparent` con el mismo `traceId`,
aunque se ejecuten en el virtual thread del TimeLimiter (el `ResilientExecutor` propaga MDC y contexto de traza). El frontend genera un
`X-Request-Id` por request y nginx lo reenvía (o genera uno si falta). Los errores mostrados al usuario incluyen
el requestId como "código de seguimiento".

## Política de datos sensibles

- Nunca se registran: contraseñas, tokens, API keys, credenciales de MinIO, cookies, headers `Authorization`,
  bodies, contenido de archivos, query strings ni URLs firmadas completas.
- El access log solo registra método, path (sin query), status, duración, IP remota y tamaño.
- Defensa en profundidad: `MaskingJsonGeneratorDecorator` enmascara campos (`password`, `secret*`, `accessKey`,
  `token`, `apiKey`, `authorization`, `cookie`…) y valores (`password=…`, `Bearer …`, `X-Amz-Signature=…`,
  `X-Amz-Credential=…`) en todos los logs JSON. Logstash además elimina esos campos.
- **Datos personales de las consultas** (nombre, email, teléfono, mensaje): se guardan solo en la base. No se
  registran en logs (los eventos de negocio llevan únicamente `departamentoId` y `consultaId`), no se devuelven en
  la respuesta del alta y los `toString()` de la entidad y del DTO los omiten.
- Errores HTTP sin stack traces, SQL, nombres de constraints ni detalles de infraestructura (`GlobalExceptionHandler`,
  `ApiErrorController` y `server.error.include-*: never`).
- Actuator sin detalles de health; endpoints sensibles no expuestos; `/actuator/info` (público) solo con datos del
  build, sin la versión de la JVM.
- Hibernate no registra los mensajes de PostgreSQL (`SqlExceptionHelper` deshabilitado): en una violación de
  unicidad incluyen los valores de la fila. El handler global registra solo el nombre de la constraint.
- nginx agrega `Content-Security-Policy` estricta (sin scripts ni estilos inline; imágenes solo del propio origen,
  `blob:` y MinIO), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y `Referrer-Policy` en todas las
  respuestas, una sola vez (oculta los del backend en las respuestas proxeadas). Los E2E fallan ante cualquier
  violación de CSP.
- Secretos solo por variables de entorno; `.env` ignorado por Git.

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
un prefijo único por ejecución. Detalles en [frontend/README.md](frontend/README.md#e2e-playwright).

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

## Decisiones técnicas

- **Spring Boot 3.5.x + Java 21**, virtual threads habilitados (`spring.threads.virtual.enabled`).
- **Maven Wrapper** (`backend/mvnw`, Maven 3.9.11, la misma línea que la imagen de build de Docker): no requiere Maven instalado.
- **Seguridad**: la API de dominio es pública (el desafío no define usuarios); Spring Security se usa para proteger
  Actuator con HTTP Basic, stateless, sin CSRF (no hay cookies de sesión). Si falta `ACTUATOR_PASSWORD` se usa una
  contraseña aleatoria no registrada (metrics/prometheus quedan inaccesibles) — *fail-safe*.
- **Readiness = readinessState + db**. MinIO y el proveedor de direcciones quedan fuera: no son necesarios para
  atender la mayoría de las operaciones y su caída no debe sacar la instancia de servicio.
- **MinIO SDK 8.5.x**: 8.6+ y 9.x usan OkHttp 5, que requiere Kotlin 2; Spring Boot 3.5 gestiona Kotlin 1.9, y
  mezclarlos arriesga errores en ejecución.
- **Resilience4j programático** (`ResilientExecutor`) en lugar de anotaciones: un solo lugar con el orden de los
  decoradores, sin AOP, con propagación explícita del contexto (MDC y trace) al hilo del TimeLimiter y fallbacks
  tipados en cada servicio.
- **Frontend: la URL como estado del listado** y validación del contrato con Zod en runtime; formularios con las
  mismas reglas que el backend; fotos de a una con estado propio (errores parciales manejables).
- **Plantilla de índice explícita + `ignore_malformed`** en Elasticsearch en lugar del mapeo dinámico: filtros
  exactos sobre `keyword` y ningún evento rechazado por un tipo inesperado.
- **E2E con el Chrome instalado** (`channel: 'chrome'`): evita descargar navegadores para correrlos localmente; en
  CI se usa el Chromium de Playwright con `E2E_BROWSER_CHANNEL=chromium`.
- **`shutdownGracePeriod` de 5 s** en el appender de Logstash: el default (1 minuto) demoraba el apagado con
  Logstash caído; lo detectó `LogstashAppenderTest`.
- **Una foto por request**: errores parciales manejables (el cliente sabe qué foto falló) y compensación simple.
- **Fallback de direcciones = degradado, no stub**: devolver el catálogo local cuando falla Georef sería presentar
  datos falsos como reales.
- **Edición con `PUT` (reemplazo completo) + `ETag`/`If-Match`** en lugar de `PATCH`: el formulario de edición
  envía siempre el recurso completo y el ETag protege contra la pérdida de actualizaciones entre usuarios.
- **`Location` relativo** en el alta: no depende del header `Host`, que detrás del proxy es el host interno.
- **`codigo` comercial** generado por el backend (Crockford Base32, sin caracteres ambiguos): referencia legible
  para el negocio y clave natural del seed. El `UNIQUE` de la base es la garantía final ante colisiones.
- **Mappers manuales** en lugar de MapStruct: pocos DTOs, sin procesador de anotaciones y con la normalización
  de textos explícita.
- **Validación de método de Spring 6.1**: cuando un handler tiene restricciones en sus parámetros (`@Positive`
  en el id), los errores del body llegan como `HandlerMethodValidationException`; el handler global los desglosa por
  campo igual que los de `@Valid`.
- **Listado en 3 consultas** (página proyectada + `COUNT` + agregados de la página) en lugar de una sola con
  `LEFT JOIN ... GROUP BY` sobre todas las filas filtradas: el `GROUP BY` global agregaría fotos y consultas de
  todos los departamentos que cumplen el filtro antes de paginar. Así solo se agrega lo que se muestra.
- **Listado 100 % Criteria API**, sin consultas escritas como texto. Los agregados eran SQL nativo (tablas
  derivadas unidas con `LEFT JOIN`, que la Criteria API no expresa); se reemplazaron por subconsultas escalares
  correlacionadas: misma cantidad de sentencias, sin *seq scans* y tiempos equivalentes con 100k departamentos
  (0,6 ms frente a 0,7 ms).
- **Metamodelo JPA estático** (`hibernate-jpamodelgen`, vía `annotationProcessorPaths`): las Specifications, la
  proyección y los agregados no usan nombres de atributos en texto; un renombre rompe la compilación, no la ejecución.
- **`LIKE ... ESCAPE '\'` explícito**: sin él, Hibernate genera `ESCAPE ''`, que en PostgreSQL desactiva el escape y
  rompía las búsquedas con `%` o `_` (lo detectó `ListadoIT`).
- **Precio ordenado dentro de cada moneda** (`moneda, precio, id`): ARS y USD no son comparables; filtrar por precio
  exige `moneda`.
- **nginx re-resuelve el upstream** (`resolver` + variable en `proxy_pass`): sin esto, tras reiniciar el backend
  nginx seguía usando la IP vieja y respondía 502.
- **Hikari `connection-timeout` 3 s** para que el probe de readiness responda dentro del timeout del healthcheck (5 s).
- **Logstash** vía `LogstashTcpSocketAppender` (ring buffer asíncrono, `appendTimeout=0` ⇒ descarta en vez de
  bloquear). Spring Boot registra un listener que imprime en stdout, como texto plano con stack trace, los avisos
  internos de Logback (p. ej. cada reconexión fallida). `StructuredStatusListener` lo reemplaza: reemite esos
  avisos como eventos JSON (`logger=com.lebane.logging.logback`, campos `logbackOrigin` y `cause`), uno por origen
  por minuto y solo a stdout. Así, una caída de Logstash queda visible sin romper el formato ni inundar los logs.
- **Logs 100 % JSON**: además de lo anterior, la JVM recibe sus opciones por `JAVA_OPTS` y no por
  `JAVA_TOOL_OPTIONS`, que imprime "Picked up …" en texto; los encoders usan `includeContext=false` para no
  filtrar propiedades internas (`LOGSTASH_HOST`, …).
- **Esquema de BD**: migraciones Flyway versionadas; `ddl-auto=validate`.
- **open-in-view deshabilitado**, `fail_on_pagination_over_collection_fetch=true` (previene paginación en memoria).
- **Frontend en Docker** servido por nginx unprivileged con proxy a la API ⇒ mismo origen, sin CORS.
- **MinIO**: las imágenes oficiales (`minio/minio` en Docker Hub y `quay.io/minio/minio`) ya no se pueden
  descargar públicamente. Se usa `cgr.dev/chainguard/minio`, construida por Chainguard desde el código oficial de
  MinIO, que incluye `mc` para el healthcheck. Está **fijada por digest** porque el tier gratuito solo publica
  `latest`. Es configurable con `MINIO_IMAGE`; para actualizarla, `docker pull cgr.dev/chainguard/minio:latest` y
  copiar el nuevo digest.
- **Testcontainers 1.21.4** (sobrescribe la 1.21.3 de Boot 3.5.7): la anterior no es compatible con Docker
  Engine 29+, que exige API ≥ 1.44.
- **CSP en nginx y no en el backend**: es quien sirve el HTML; el origen de las fotos llega por variable de entorno
  (`IMAGES_ORIGIN`), así la política sigue a `STORAGE_PUBLIC_URL` sin reconstruir la imagen.
- **Healthcheck del frontend contra `127.0.0.1`**: dentro del contenedor `localhost` resuelve primero a `::1` y
  nginx escucha solo en IPv4.

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

## Limitaciones conocidas

- `/actuator/health` (raíz) incluye `"groups":["liveness","readiness"]`: es el comportamiento estándar de Spring
  Boot con grupos de health y solo expone nombres, no detalles. Liveness y readiness devuelven exactamente
  `{"status":"UP"}`.
- Con `LOGSTASH_ENABLED=true` y Logstash caído, el aviso de conexión aparece como evento JSON al primer fallo; el
  appender de logstash-logback-encoder deja de reportar los reintentos siguientes. Los eventos de la caída se
  retienen en memoria y se envían al reconectar (validado: 50 de 50); los que excedan el buffer (8.192) o los
  pendientes si el backend se reinicia durante la caída se pierden en Logstash, pero siempre quedan en stdout.
- Los E2E crean datos reales en la base contra la que corren (con prefijo `E2E…`): la API no permite borrar
  departamentos. Conviene correrlos contra un entorno de desarrollo o de pruebas, no contra uno con datos reales.
- Las URLs mal formadas (`%` suelto, `%2F` codificado) las rechaza Tomcat antes de llegar a la aplicación, con su
  página HTML genérica de 400 (sin versión ni detalles, pero sin requestId ni formato `ApiError`). Los rechazos del
  firewall de Spring Security sí responden `ApiError` en JSON.
- Las fotos no se pueden reordenar ni elegir cuál es la principal (es la primera subida que sigue existiendo).
- Objetos huérfanos: si falla el borrado compensatorio o el borrado en MinIO después de eliminar la fila, el objeto
  queda en el bucket (registrado en ERROR/WARN con su `objectKey`, inaccesible desde la aplicación). Falta un job
  de reconciliación periódico (listar objetos sin fila en `imagen`).
- Las fotos se validan por firma binaria, no se decodifican ni se re-encodean: un archivo con firma PNG válida pero
  contenido corrupto se acepta (el navegador mostrará el placeholder de imagen rota). No se generan miniaturas.
- El estado de los circuit breakers es por instancia del backend (en memoria); con varias réplicas, cada una abre y
  cierra su propio circuito.
- Georef es un servicio público sin SLA y con límites de uso; para producción con tráfico alto convendría cache de
  sugerencias o un proveedor con contrato.
- Paginación por `OFFSET`, limitada a los primeros 10.000 resultados de cada búsqueda. Para recorridos completos
  (exportaciones, scroll infinito profundo) convendría paginación por cursor (*keyset*), fuera del alcance.
- La búsqueda de texto distingue acentos (`balcon` no encuentra `balcón`) y solo busca en el título. Ignorar acentos
  requiere `unaccent` con un wrapper `IMMUTABLE` indexable; queda como mejora.
- El total exacto (`totalElements`) de una búsqueda poco selectiva cuesta O(n) sobre un índice (16 ms con 100k
  filas). Con varios millones de filas convendría un total estimado o un `COUNT` acotado.
- La API de dominio es pública (el desafío no define usuarios ni roles). Cualquier cliente puede crear y editar
  departamentos; agregar autenticación de usuarios queda fuera del alcance.
- `ConsultaRequest.email` usa la validación de `@Email` de Hibernate Validator (sintáctica); no se verifica que el
  buzón exista.
