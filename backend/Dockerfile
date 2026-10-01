# syntax=docker/dockerfile:1.7
# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
# Cache de dependencias independiente del código fuente
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
# Los tests se ejecutan en CI / localmente (requieren Docker para Testcontainers).
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/lebane-backend.jar extract --layers --launcher --destination extracted

# ---------- Runtime ----------
# Alpine: incluye wget (busybox) para el healthcheck de readiness.
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S lebane && adduser -S -G lebane -H -s /sbin/nologin lebane
WORKDIR /app
COPY --from=build --chown=lebane:lebane /workspace/extracted/dependencies/ ./
COPY --from=build --chown=lebane:lebane /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=lebane:lebane /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=lebane:lebane /workspace/extracted/application/ ./
USER lebane:lebane
EXPOSE 8080
# Opciones de JVM vía JAVA_TOOL_OPTIONS (definidas en docker-compose / .env).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
