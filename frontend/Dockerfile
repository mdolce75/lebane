# syntax=docker/dockerfile:1.7
# ---------- Build ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN --mount=type=cache,target=/root/.npm \
    if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY . .
# Variables públicas de build (no secretos)
ARG VITE_API_BASE_URL=/api
ARG VITE_HEALTH_URL=/actuator/health/readiness
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL} VITE_HEALTH_URL=${VITE_HEALTH_URL}
RUN npm run build

# ---------- Runtime ----------
# nginx sin privilegios (escucha en 8080). Incluye wget (busybox) para el healthcheck.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_UPSTREAM=http://backend:8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/ || exit 1
