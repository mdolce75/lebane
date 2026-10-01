# lebane-backend

API REST de Lebane — Spring Boot 3.5 · Java 21 · Maven. Documentación completa en el [README raíz](../README.md).

## Comandos

```bash
./mvnw test               # tests unitarios (*Test); no requieren Docker
./mvnw verify             # + tests de integración (*IT) con Testcontainers; requiere Docker
./mvnw spring-boot:run    # requiere PostgreSQL (docker compose up -d postgres minio)
docker build -t lebane-backend:local .
```

Variables mínimas para `spring-boot:run`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ACTUATOR_PASSWORD`.
Para logs legibles en consola: `LOG_FORMAT=text`. Para datos de ejemplo: `SEED_ENABLED=true`.
Flyway aplica las migraciones al arrancar.

## Paquetes

| Paquete | Responsabilidad |
|---|---|
| `config` | Seguridad (Actuator con HTTP Basic), CORS, auditoría JPA, `@ConfigurationProperties` |
| `departamento.controller` | `DepartamentoController` (`/api/v1/departamentos`), ETags |
| `departamento.dto` | Requests/responses (records) y validaciones entre campos (`dto.validation`) |
| `departamento.entity` | `Departamento` (+ `Direccion` embebida), `Imagen`, `Consulta`; relaciones LAZY, sin colecciones |
| `departamento.mapper` | Conversión DTO ↔ entidad y normalización de textos |
| `departamento.repository` | Spring Data JPA + `JpaSpecificationExecutor`; `DepartamentoSpecifications` (filtros); fragmento `DepartamentoListadoRepository` (página proyectada con Criteria y agregados por página) |
| `departamento.service` | Casos de uso transaccionales (alta, edición, detalle, listado, consultas) y generador de códigos |
| `storage` | `ImageUrlResolver` (URL pública de imágenes); cliente MinIO en Fase 4 |
| `logging` | `RequestContext` (correlation ID), `AvailabilityStateLogger`, `StructuredStatusListener` |
| `logging.filter` | `RequestIdFilter`: X-Request-Id, MDC y access log estructurado |
| `logging.interceptor` | `RequestIdPropagationInterceptor` para clientes HTTP salientes |
| `exception` | `ApiError`, `ErrorCode`, excepciones de dominio, `GlobalExceptionHandler`, `ApiErrorController` |
| `seed` | `DevDataSeeder` idempotente (`SEED_ENABLED`) |
| `address`, `resilience` | Fase 4 |

## Recursos

| Archivo | Contenido |
|---|---|
| `application.yml` | Configuración por variables de entorno, Flyway, Actuator, health groups, métricas, tracing |
| `db/migration/V*.sql` | Migraciones Flyway (V1 esquema y constraints, V2 índices del listado) |
| `logback-spring.xml` | Selección de salida según `LOG_FORMAT` / `LOGSTASH_ENABLED` |
| `logback-format-json.xml` | JSON a stdout con enmascarado de secretos (`LOG_FORMAT=json`) |
| `logback-format-text.xml` | Texto legible con requestId/traceId (`LOG_FORMAT=text`) |
| `logback-logstash-true.xml` | Appender TCP asíncrono y no bloqueante a Logstash (`LOGSTASH_ENABLED=true`) |

## Tests de performance

`src/test/resources/perf/`: `datos-volumen.sql` (100k departamentos sintéticos) y `explain-listado.sql`
(`EXPLAIN ANALYZE` de las consultas del listado). `ListadoPerformanceIT` los usa con un PostgreSQL propio; el
procedimiento manual está en el README raíz, sección "Validación de performance".
