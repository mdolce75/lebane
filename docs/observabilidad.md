# Observabilidad y seguridad

[← Volver al README](../README.md)

- [Actuator, liveness y readiness](#actuator-liveness-y-readiness)
- [Logging estructurado](#logging-estructurado)
- [Correlation ID](#correlation-id)
- [Política de datos sensibles](#política-de-datos-sensibles)

---

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
  readiness. **MinIO tampoco**: solo lo usan las subidas y bajas de fotos (ver [Imágenes y MinIO](arquitectura.md#imágenes-y-minio)).
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
