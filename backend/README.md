# lebane-backend

API REST de Lebane — Spring Boot 3.5 · Java 21 · Maven. Documentación completa en el [README raíz](../README.md).

## Comandos

```bash
mvn test                 # tests unitarios (*Test); no requieren Docker
mvn verify               # + tests de integración (*IT) con Testcontainers; requiere Docker
mvn spring-boot:run      # requiere PostgreSQL (docker compose up -d postgres minio)
docker build -t lebane-backend:local .
```

Variables mínimas para `spring-boot:run`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ACTUATOR_PASSWORD`.
Para logs legibles en consola: `LOG_FORMAT=text`.

## Paquetes

| Paquete | Responsabilidad |
|---|---|
| `config` | Seguridad (Actuator con HTTP Basic), CORS, `@ConfigurationProperties` |
| `logging` | `RequestContext` (correlation ID), `AvailabilityStateLogger` |
| `logging.filter` | `RequestIdFilter`: X-Request-Id, MDC y access log estructurado |
| `logging.interceptor` | `RequestIdPropagationInterceptor` para clientes HTTP salientes |
| `exception` | `ApiError` (contrato de error) y `ErrorCode` |
| `departamento`, `address`, `storage`, `resilience`, `seed` | Fases 2–4 |

## Recursos

| Archivo | Contenido |
|---|---|
| `application.yml` | Configuración por variables de entorno, Actuator, health groups, métricas, tracing |
| `logback-spring.xml` | Selección de salida según `LOG_FORMAT` / `LOGSTASH_ENABLED` |
| `logback-format-json.xml` | JSON a stdout con enmascarado de secretos (`LOG_FORMAT=json`) |
| `logback-format-text.xml` | Texto legible con requestId/traceId (`LOG_FORMAT=text`) |
| `logback-logstash-true.xml` | Appender TCP asíncrono y no bloqueante a Logstash (`LOGSTASH_ENABLED=true`) |
