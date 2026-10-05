# Decisiones técnicas

[← Volver al README](../README.md)

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
- **Baja lógica** (`fecha_baja`) en lugar de borrado físico: las consultas de los interesados son datos del negocio
  y no se pierden; la dirección queda libre para otro aviso, y la baja se puede revertir (reactivación). Un dado de
  baja responde `409` al modificarlo y no `404`: existe, se lee y se reactiva; lo que no admite son cambios. El filtro es explícito con Criteria
  (`noDadoDeBaja`) y no `@SoftDelete`/`@SQLRestriction` de Hibernate, que lo aplicarían a todas las consultas,
  también a las del seed, que necesita ver los dados de baja para no recrearlos.
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
- **Backend 100 % Criteria API**: ninguna consulta escrita como texto ni derivada del nombre del método. Los
  repositorios exponen métodos `default` sobre Specifications (legibles en el servicio y fáciles de mockear) y
  fragmentos con Criteria para las proyecciones y el bloqueo de fila. Un renombre en las entidades rompe la
  compilación, no la ejecución.
- **Listado 100 % Criteria API**. Los agregados eran SQL nativo (tablas
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
- **Filtro de prefijos de Maven desactivado** (`backend/.mvn/maven.config`): Maven 3.10 descarga `/.meta/prefixes.txt`
  de cada repositorio remoto. El POM padre de Flyway (`flyway-parent`) declara un repositorio de GitHub Packages que
  responde 401 incluso para leer; Maven no cachea ese error (sí los 404 de los demás) y lo reportaba como `WARNING`
  en cada build. Sin el filtro el build es igual de rápido: casi todo viene de Maven Central y queda en la caché.
- **Testcontainers 1.21.4** (sobrescribe la 1.21.3 de Boot 3.5.7): la anterior no es compatible con Docker
  Engine 29+, que exige API ≥ 1.44.
- **CSP en nginx y no en el backend**: es quien sirve el HTML; el origen de las fotos llega por variable de entorno
  (`IMAGES_ORIGIN`), así la política sigue a `STORAGE_PUBLIC_URL` sin reconstruir la imagen.
- **Healthcheck del frontend contra `127.0.0.1`**: dentro del contenedor `localhost` resuelve primero a `::1` y
  nginx escucha solo en IPv4.
