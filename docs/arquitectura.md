# Arquitectura

[← Volver al README](../README.md)

- [Arquitectura](#arquitectura)
- [Modelo de datos](#modelo-de-datos)
- [Imágenes y MinIO](#imágenes-y-minio)
- [Autocompletado de direcciones](#autocompletado-de-direcciones)
- [Resiliencia (Resilience4j)](#resiliencia-resilience4j)
- [Frontend](#frontend)

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
│       │                   repository: Specifications + fragmentos Criteria (listado, id/bloqueo)
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
| `consulta` | nombre, email, teléfono, mensaje, `created_at` (datos personales: nunca en logs; solo los devuelve el listado de consultas del departamento) | FK + índice `ix_consulta_departamento_fecha` (conteo y listado paginado) |

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
  `(superficie_m2, id)`) y un GIN de trigramas sobre `f_unaccent(lower(titulo))` para la búsqueda de texto sin
  distinguir mayúsculas ni acentos. Ver
  [Validación de performance](api.md#validación-de-performance).

## Imágenes y MinIO

```bash
# Agregar una foto a un departamento existente (en el alta van en el mismo POST multipart; el tipo se detecta
# por el contenido, no por el nombre)
curl -i -F "archivo=@casa.jpg" http://localhost:8080/api/departamentos/1/imagenes
# HTTP/1.1 201
# {"id":19,"url":"http://localhost:9000/lebane-images/departamentos/1/80d7…png","contentType":"image/png",
#  "sizeBytes":8600,"posicion":0}

curl -i -X DELETE http://localhost:8080/api/departamentos/1/imagenes/19      # 204
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
curl 'http://localhost:8080/api/direcciones/autocompletar?q=Av%20Santa%20Fe%201860&limite=2'
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
| Listado | `/departamentos` (`/` redirige) | Tarjetas con foto principal, precio, ubicación, características y contadores. Filtros (texto, ciudad, estado, moneda + precio, superficie mínima y máxima, ambientes, con/sin fotos), orden y tamaño de página |
| Alta | `/departamentos/nuevo` | Formulario validado, autocompletado de dirección y hasta 5 fotos con vista previa |
| Detalle | `/departamentos/:id` | Galería, datos completos, mapa (si hay coordenadas), formulario de consulta y consultas recibidas (paginadas, con los datos de contacto) |
| Edición | `/departamentos/:id/editar` | Mismo formulario con concurrencia optimista y gestión de fotos (eliminar / subir) antes de Guardar |

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
  ambientes, coordenadas completas). En los filtros del listado la pantalla pide elegir la moneda para filtrar por
  precio, para que el usuario sepa en qué moneda está el rango (la API, si falta, asume `USD`). Los `fieldErrors` de un 400 del
  servidor se muestran en el campo correspondiente porque los nombres coinciden (`direccion.ciudad`).
- **Dirección con autocompletado** (*debounce* de 300 ms, mínimo 3 caracteres): el buscador es el único campo de
  dirección; al elegir una sugerencia se muestra como resumen (calle y altura, ciudad, provincia) y se completan
  las coordenadas, redondeadas a 6 decimales. Piso y unidad se cargan aparte porque el proveedor no los trae.
  Los campos sueltos aparecen solo para cargarla a mano: si no hay sugerencias, si el proveedor está degradado o si
  la sugerencia no trae altura.
- **Orden del formulario**: datos, dirección, fotos y recién al final Guardar y Cancelar, en el alta y en la
  edición. En la edición, quitar una foto actual es inmediato (con confirmación) y las nuevas se suben al guardar.
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
