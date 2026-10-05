# API

[← Volver al README](../README.md)

- [Endpoints](#endpoints)
- [Errores](#errores)
- [Listado: paginación, filtros y orden](#listado-paginación-filtros-y-orden)
- [Validación de performance](#validación-de-performance)

---

## Endpoints

API REST versionada bajo `/api/v1`. JSON en request y response. En Docker el navegador la consume vía el proxy del
frontend (`http://localhost:3000/api/...`, mismo origen); directo en `http://localhost:8080/api/...`.

**Documentación OpenAPI 3.1** (springdoc, generada desde el código):

- Swagger UI para explorar y probar la API: http://localhost:8080/swagger-ui.html
- Spec en JSON: http://localhost:8080/v3/api-docs; versionado en [`docs/openapi.json`](openapi.json)
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
| `POST` | `/api/v1/departamentos` | Alta (no como `VENDIDO` ni en la dirección de otro publicado) | `201` + `Location` + `ETag` · `400` · `409` |
| `GET` | `/api/v1/departamentos/{id}` | Detalle completo (dirección, imágenes ordenadas, cantidad de consultas) | `200` + `ETag` · `400` · `404` |
| `PUT` | `/api/v1/departamentos/{id}` | Edición (reemplazo completo); `If-Match` opcional; un `VENDIDO` no se edita | `200` + `ETag` · `400` · `404` · `409` · `412` |
| `DELETE` | `/api/v1/departamentos/{id}` | Baja lógica (ver abajo); `If-Match` opcional | `204` · `400` · `404` · `412` |
| `POST` | `/api/v1/departamentos/{id}/consultas` | Registrar una consulta de un interesado | `201` · `400` · `404` · `409` (vendido o repetida) |
| `POST` | `/api/v1/departamentos/{id}/imagenes` | Subir una foto (`multipart/form-data`, campo `archivo`) ([detalle](arquitectura.md#imágenes-y-minio)) | `201` · `400` · `404` · `409` · `413` · `503` |
| `DELETE` | `/api/v1/departamentos/{id}/imagenes/{imagenId}` | Eliminar una foto | `204` · `404` · `409` (vendido) |
| `GET` | `/api/v1/direcciones/autocompletar?q=` | Autocompletado de direcciones ([detalle](arquitectura.md#autocompletado-de-direcciones)) | `200` · `400` |

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

**Ciclo de vida del estado** (`EstadoDepartamento`):

```
DISPONIBLE ⇄ RESERVADO
    │           │
    └──► VENDIDO ◄┘   (final)
```

- Un departamento no se publica directamente como `VENDIDO` (`409 TRANSICION_DE_ESTADO_INVALIDA`).
- Un departamento `VENDIDO` es un registro cerrado: no cambia de estado, no se edita (ni sus datos ni sus fotos) y no
  recibe consultas (`409 DEPARTAMENTO_NO_DISPONIBLE`). Si además el `If-Match` está desactualizado, primero responde
  `412`, para que el cliente recargue y vea el estado actual.
- Las reglas viven en el enum (`transicionesPermitidas`, `esModificable`, `admiteAlta`) y se aplican en los servicios;
  el frontend las replica para no ofrecer opciones que el backend va a rechazar.

**Baja lógica**:

- `DELETE /api/v1/departamentos/{id}` no borra el registro: guarda la fecha en `fecha_baja`. Desde ese momento el
  departamento no aparece en el listado ni cuenta en el total, y el detalle, la edición, las fotos, las consultas y
  una segunda baja responden `404`, igual que si no existiera.
- Se conservan el departamento, sus fotos (también en MinIO) y sus consultas, como historial. La dirección queda libre
  para otro aviso.
- Se puede dar de baja en cualquier estado, también vendido, para sacarlo del listado. Es definitiva.
- Con `If-Match` desactualizado responde `412` sin darlo de baja, igual que la edición.
- Una operación en curso sobre el mismo departamento no le agrega datos después de la baja: subir una foto y enviar
  una consulta bloquean la fila (`SELECT … FOR UPDATE`) solo si sigue vigente, y la baja también toma ese lock.
- Todas las consultas filtran `fecha_baja IS NULL` con la Criteria API (`DepartamentoSpecifications.noDadoDeBaja`).
  No se usa `@SQLRestriction` ni `@SoftDelete` de Hibernate: el filtro queda explícito y el seed puede ver los dados
  de baja para no volver a crearlos.

**Duplicados**:

- **Avisos**: no puede haber dos departamentos publicados (`DISPONIBLE` o `RESERVADO`) en la misma unidad física:
  calle, número, piso, unidad, ciudad y provincia, sin distinguir mayúsculas (`409 AVISO_DUPLICADO`). No se comparan
  código postal ni coordenadas, que pueden cargarse o no. Al editar, se controla solo si cambia la dirección, así un
  aviso cargado antes de la regla sigue siendo editable. Un departamento vendido libera la dirección.
- **Consultas**: el mismo email (sin distinguir mayúsculas) no consulta dos veces por el mismo departamento en 24 horas
  (`409 CONSULTA_DUPLICADA`). Evita dobles envíos y spam. El control bloquea la fila del departamento
  (`SELECT … FOR UPDATE`), así que dos envíos simultáneos no pasan ambos.
- La consulta de duplicados es type-safe (Criteria API y metamodelo) y usa índices propios
  (`V3__indices_reglas_duplicados.sql`). La regla de avisos no es un índice único a propósito: una base existente puede
  tener duplicados de antes y la migración fallaría. El costo es que dos altas idénticas exactamente simultáneas
  podrían pasar ambas.

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
| `DEPARTAMENTO_NO_DISPONIBLE` | 409 | Regla de negocio: un departamento vendido no se edita, no cambia sus fotos ni recibe consultas |
| `TRANSICION_DE_ESTADO_INVALIDA` | 409 | Regla de negocio: cambio de estado no permitido (p. ej. publicar directamente como vendido) |
| `AVISO_DUPLICADO` | 409 | Regla de negocio: ya hay un departamento publicado en la misma dirección |
| `CONSULTA_DUPLICADA` | 409 | Regla de negocio: el mismo email ya consultó por el departamento en las últimas 24 horas |
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

**Sin consultas escritas como texto, en todo el backend**: página, filtros, orden, total y agregados del listado, y
también las demás consultas (fotos y consultas de un departamento, reglas de negocio, bloqueo de fila, seed), se
construyen con la Criteria API y el metamodelo estático (`Departamento_`, `Imagen_`, ...). Ni `@Query` con JPQL/SQL,
ni `createQuery(String)`, ni SQL nativo, ni concatenación de cadenas; los nombres de atributos tampoco van en texto.
Tampoco hay consultas derivadas del nombre del método: los repositorios exponen métodos `default` sobre
Specifications (`ImagenSpecifications`, `ConsultaSpecifications`, `DepartamentoSpecifications`) y fragmentos
implementados con Criteria (`DepartamentoListadoRepositoryImpl`, `DepartamentoClaveRepositoryImpl`).
`SinConsultasDeTextoTest` hace fallar el build si aparece una consulta de texto en cualquier archivo del backend o un
método de consulta abstracto en un repositorio.

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
| Página por defecto (`created_at DESC`) | `Index Scan Backward` en `ix_departamento_created` + `Limit` | 0,1 ms |
| Página profunda (offset 9.900, 100 filas) | `Index Scan Backward` en `ix_departamento_created` (lee 10.000 entradas) | 4,1 ms |
| `estado = VENDIDO` (página) | `Index Scan Backward` en `ix_departamento_estado_created` | 0,1 ms |
| `estado = VENDIDO` (`COUNT`) | `Index Only Scan` en `ix_departamento_estado_created`, `Heap Fetches: 0` | 4,7 ms |
| `ciudad = rosario` (página) | `Index Scan Backward` en `ix_departamento_ciudad_created` | 0,08 ms |
| `ciudad = rosario` (`COUNT`) | `Bitmap Index Scan` en `ix_departamento_ciudad_created` | 7,3 ms |
| USD entre 100k y 200k, orden por precio | `Index Scan` en `ix_departamento_moneda_precio` (rango en el índice) | 0,08 ms |
| `q = balcón` (página) | `Index Scan Backward` en `ix_departamento_created` + filtro | 0,2 ms |
| `q = reciclado` + ciudad (`COUNT`) | `BitmapAnd` de `ix_departamento_ciudad_created` e `ix_departamento_titulo_trgm` (trigramas) | 12,4 ms |
| `COUNT` sin filtros | `Index Only Scan` en `ix_departamento_vigentes`, `Heap Fetches: 0` | 15,3 ms |
| Agregados de 20 IDs | Subconsultas correlacionadas: `Index Only Scan` en `uk_imagen_departamento_posicion` e `ix_consulta_departamento_email` (`Heap Fetches: 0`), `Index Scan` para la foto principal | 0,5 ms |

Ningún plan contiene `Seq Scan`. Cuando el filtro es poco selectivo, PostgreSQL elige recorrer el índice del orden
y filtrar hasta completar la página (más barato que usar el índice del filtro y ordenar). Un `COUNT` exacto debe
contar todas las filas que cumplen el filtro: sin filtros es O(n) sobre el índice más chico (15 ms con 100k filas).
Por eso se omite cuando la página permite deducir el total.

Todas las consultas del listado filtran `fecha_baja IS NULL` (baja lógica). Los índices del listado son parciales con
esa misma condición (`V4__baja_logica.sql`): siguen sirviendo para los `COUNT` sin leer la tabla y no guardan los
dados de baja. La excepción es el de trigramas: como índice parcial, PostgreSQL perdía las estadísticas de
`lower(titulo)` y elegía un plan peor para la búsqueda de texto.

Reproducir sobre una base descartable, sin tocar la de desarrollo:

```bash
P="docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U lebane"
$P -d lebane -c "CREATE DATABASE lebane_perf"
$P -d lebane_perf < backend/src/main/resources/db/migration/V1__esquema_inicial.sql
$P -d lebane_perf < backend/src/main/resources/db/migration/V2__indices_listado.sql
$P -d lebane_perf < backend/src/main/resources/db/migration/V3__indices_reglas_duplicados.sql
$P -d lebane_perf < backend/src/main/resources/db/migration/V4__baja_logica.sql
$P -d lebane_perf < backend/src/test/resources/perf/datos-volumen.sql
$P -d lebane_perf -c "VACUUM ANALYZE departamento, imagen, consulta"
$P -d lebane_perf < backend/src/test/resources/perf/explain-listado.sql
$P -d lebane -c "DROP DATABASE lebane_perf"
```
