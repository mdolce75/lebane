# Lebane

Aplicación inmobiliaria para gestionar **departamentos en venta**: API REST (Spring Boot 3 / Java 21 /
PostgreSQL / MinIO) y panel de administración (React + TypeScript + Vite), orquestados con Docker Compose y con
un perfil opcional de observabilidad (Elasticsearch + Logstash + Kibana).

> **Estado:** Fases 1 a 3 implementadas **y verificadas**. Fase 1: infraestructura, Actuator, health probes,
> logging JSON, correlation ID, Docker Compose y perfil ELK. Fase 2: modelo de datos, migraciones Flyway, API de
> alta / detalle / edición / consultas, validaciones, manejo global de errores y seed idempotente. Fase 3: listado
> paginado con Specifications y Criteria API, agregados en PostgreSQL, índices y validación sin full scans ni N+1
> con 100k departamentos. Ver [Estado por fase](#estado-por-fase) y [Limitaciones conocidas](#limitaciones-conocidas).

---

## Tabla de contenidos

- [Arquitectura](#arquitectura)
- [Modelo de datos](#modelo-de-datos)
- [Requisitos](#requisitos)
- [Variables de entorno](#variables-de-entorno)
- [Ejecución con Docker Compose](#ejecución-con-docker-compose)
- [Ejecución sin Docker](#ejecución-sin-docker)
- [Perfil de observabilidad](#perfil-de-observabilidad)
- [Endpoints](#endpoints)
- [Errores](#errores)
- [Seed de datos](#seed-de-datos)
- [Listado: paginación, filtros y orden](#listado-paginación-filtros-y-orden)
- [Validación de performance](#validación-de-performance)
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
│       ├── config          Seguridad, CORS, auditoría JPA, propiedades
│       ├── departamento    controller · dto (+ validation) · entity · mapper · repository · service
│       │                   repository: Specifications + fragmento Criteria/SQL del listado
│       ├── address         client · dto · provider · service                          (Fase 4)
│       ├── storage         config · service: URL pública de imágenes; subida a MinIO en Fase 4
│       ├── resilience      Configuración y eventos de Resilience4j                    (Fase 4)
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
| `STORAGE_PUBLIC_URL` / `STORAGE_BUCKET` | `http://localhost:9000` / `lebane-images` | URL pública con la que el navegador accede a las imágenes. |
| `STORAGE_*` (resto) | ver `.env.example` | Endpoint interno y credenciales de MinIO (Fase 4). |
| `SEED_ENABLED` | `false` (`.env.example`: `true`) | Carga datos de ejemplo idempotentes al arrancar. |
| `FLYWAY_ENABLED` | `true` | Aplica las migraciones al arrancar. |
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
| `NGINX_RESOLVER` | `127.0.0.11` | DNS que usa nginx para re-resolver el upstream (DNS embebido de Docker). |
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
./mvnw spring-boot:run

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

API REST versionada bajo `/api/v1`. JSON en request y response. En Docker el navegador la consume vía el proxy del
frontend (`http://localhost:3000/api/...`, mismo origen); directo en `http://localhost:8080/api/...`.

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `GET` | `/api/v1/departamentos` | Listado paginado con filtros y orden ([detalle](#listado-paginación-filtros-y-orden)) | `200` · `400` |
| `POST` | `/api/v1/departamentos` | Alta | `201` + `Location` + `ETag` · `400` · `409` |
| `GET` | `/api/v1/departamentos/{id}` | Detalle completo (dirección, imágenes ordenadas, cantidad de consultas) | `200` + `ETag` · `400` · `404` |
| `PUT` | `/api/v1/departamentos/{id}` | Edición (reemplazo completo); `If-Match` opcional | `200` + `ETag` · `400` · `404` · `409` · `412` |
| `POST` | `/api/v1/departamentos/{id}/consultas` | Registrar una consulta de un interesado | `201` · `400` · `404` · `409` (vendido) |
| `POST`/`DELETE` | `/api/v1/departamentos/{id}/imagenes` | Subida y eliminación de fotos | Fase 4 |
| `GET` | `/api/v1/direcciones/autocompletar` | Autocomplete de direcciones | Fase 4 |

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
- No depende de servicios externos. Las fotos de ejemplo se agregan con la integración de MinIO (Fase 4).
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
3. Agregados Solo para los IDs de la página, en una consulta:
             subconsultas GROUP BY sobre imagen y consulta (COUNT, MIN(posicion)) unidas con LEFT JOIN,
             + JOIN a la imagen de menor posición = imagen principal.
```

- **Sin full scans**: cada filtro y orden usa un índice (ver [Validación de performance](#validación-de-performance)).
- **Sin N+1**: la cantidad de consultas no depende del tamaño de página ni de las fotos o consultas
  (`ListadoIT`, `ListadoPerformanceIT`). No se cargan entidades (`entityLoadCount = 0`).
- **Sin multiplicación de filas**: los filtros nunca hacen JOIN a colecciones (`conImagenes` usa `EXISTS`), así que
  el `COUNT` es directo. Los agregados se agrupan por tabla **antes** del JOIN: a lo sumo una fila por
  departamento, por lo que `COUNT(*)` es exacto y no hace falta `COUNT(DISTINCT ...)`. Unir `departamento ×
  imagen × consulta` y luego agrupar multiplicaría filas (fotos × consultas) y obligaría a `COUNT(DISTINCT)` sobre
  ese producto.
- **SQL nativo solo para los agregados**: las tablas derivadas en el `FROM` (subconsultas agregadas unidas con
  `LEFT JOIN`) no existen en JPQL ni en la Criteria API estándar. La consulta es fija y con parámetros enlazados.
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
| Agregados de 20 IDs | `Index Only Scan` en `uk_imagen_departamento_posicion` e `ix_consulta_departamento` + `GroupAggregate` | 0,4 ms |

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
- **Datos personales de las consultas** (nombre, email, teléfono, mensaje): se guardan solo en la base. No se
  registran en logs (los eventos de negocio llevan únicamente `departamentoId` y `consultaId`), no se devuelven en
  la respuesta del alta y los `toString()` de la entidad y del DTO los omiten.
- Errores HTTP sin stack traces, SQL, nombres de constraints ni detalles de infraestructura (`GlobalExceptionHandler`,
  `ApiErrorController` y `server.error.include-*: never`).
- Actuator sin detalles de health; endpoints sensibles no expuestos.
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
cd frontend && npm install && npm run typecheck && npm run lint && npm test && npm run build
```

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
| `ListadoIT` | filtros, orden, paginación, imagen principal y contadores calculados en PostgreSQL, escape de `%`/`_`, 3 sentencias por página |
| `ListadoPerformanceIT` | 100k departamentos: sin *seq scans* (`pg_stat_user_tables`) y ≤ 3 sentencias en 11 escenarios, con control negativo |
| Frontend `httpClient.test.ts` | X-Request-Id, JSON/FormData, normalización de errores sin detalles internos, timeout, red |
| Frontend `App.test.tsx` | routing, indicador de readiness (UP / 503), 404, política de reintentos |
| Frontend `ErrorMessage.test.tsx`, `env.test.ts` | errores seguros con requestId, validación de configuración con Zod |

## Decisiones técnicas

- **Spring Boot 3.5.x + Java 21**, virtual threads habilitados (`spring.threads.virtual.enabled`).
- **Maven Wrapper** (`backend/mvnw`, Maven 3.9.11, la misma línea que la imagen de build de Docker): no requiere Maven instalado.
- **Seguridad**: la API de dominio es pública (el desafío no define usuarios); Spring Security se usa para proteger
  Actuator con HTTP Basic, stateless, sin CSRF (no hay cookies de sesión). Si falta `ACTUATOR_PASSWORD` se usa una
  contraseña aleatoria no registrada (metrics/prometheus quedan inaccesibles) — *fail-safe*.
- **Readiness = readinessState + db**. MinIO se decidirá en la Fase 4.
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
- **Metamodelo JPA estático** (`hibernate-jpamodelgen`, vía `annotationProcessorPaths`): las Specifications y la
  proyección no usan nombres de atributos en texto; un renombre rompe la compilación, no la ejecución.
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
- **Healthcheck del frontend contra `127.0.0.1`**: dentro del contenedor `localhost` resuelve primero a `::1` y
  nginx escucha solo en IPv4.

## Estado por fase

| Fase | Estado |
|---|---|
| 1. Infraestructura y observabilidad inicial | **Completa y verificada** (build, 44 tests unitarios + 5 IT backend, 21 tests frontend, Docker Compose con y sin perfil `observability`) |
| 2. Modelo, persistencia y errores | **Completa y verificada** (109 tests unitarios + 20 IT backend; stack Docker: Flyway, seed idempotente, API directa y vía proxy) |
| 3. Specifications, Criteria y listado optimizado | **Completa y verificada** (131 tests unitarios + 34 IT backend; EXPLAIN ANALYZE con 100k departamentos; endpoint probado directo y vía proxy) |
| 4. Storage, direcciones, resiliencia y métricas | Pendiente |
| 4.1 Logging y observabilidad (validación) | Pendiente |
| 5. Frontend | Pendiente (scaffold, cliente HTTP y routing listos) |
| 6. Tests completos | Pendiente |
| 7. Validación final | Pendiente |

## Limitaciones conocidas

- `/actuator/health` (raíz) incluye `"groups":["liveness","readiness"]`: es el comportamiento estándar de Spring
  Boot con grupos de health y solo expone nombres, no detalles. Liveness y readiness devuelven exactamente
  `{"status":"UP"}`.
- Con `LOGSTASH_ENABLED=true` y Logstash caído, el aviso de conexión aparece como evento JSON al primer fallo; el
  appender de logstash-logback-encoder deja de reportar los reintentos siguientes. Los eventos generados durante la
  caída se descartan (buffer en memoria, sin bloquear requests).
- La subida de imágenes a MinIO, el autocomplete y Resilience4j llegan en la Fase 4. Mientras tanto, `imagenes` del
  detalle e `imagenPrincipalUrl` del listado están vacíos salvo datos cargados a mano.
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
