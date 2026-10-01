#!/bin/sh
# Configuración inicial de Elasticsearch para Lebane (idempotente; servicio "elasticsearch-setup" del perfil
# observability). Credenciales solo por variables de entorno; nunca se imprimen.
#   1. Contraseña del usuario interno kibana_system.
#   2. Política ILM "lebane-logs": borra los índices diarios después de LOGS_RETENTION_DAYS días.
#   3. Plantilla de índice "lebane-logs": mapeo explícito (keyword para filtros exactos, números, fechas) e
#      ignore_malformed para que un valor con tipo inesperado no haga rechazar el evento completo.
set -eu

ES="http://elasticsearch:9200"
AUTH="elastic:${ELASTIC_PASSWORD}"
RETENTION="${LOGS_RETENTION_DAYS:-7}"

request() {
  # $1 método, $2 ruta, $3 cuerpo (archivo o JSON). Devuelve el código HTTP.
  if [ -f "$3" ]; then
    curl -s -o /tmp/response -w '%{http_code}' -u "$AUTH" -X "$1" -H 'Content-Type: application/json' "$ES$2" --data-binary "@$3"
  else
    curl -s -o /tmp/response -w '%{http_code}' -u "$AUTH" -X "$1" -H 'Content-Type: application/json' "$ES$2" -d "$3"
  fi
}

until [ "$(request POST /_security/user/kibana_system/_password "{\"password\":\"${KIBANA_SYSTEM_PASSWORD}\"}")" = "200" ]; do
  echo "Esperando a Elasticsearch..."
  sleep 5
done
echo "kibana_system configurado"

code=$(request PUT /_ilm/policy/lebane-logs \
  "{\"policy\":{\"phases\":{\"hot\":{\"actions\":{}},\"delete\":{\"min_age\":\"${RETENTION}d\",\"actions\":{\"delete\":{}}}}}}")
[ "$code" = "200" ] || { echo "Error creando la politica ILM (HTTP $code)"; cat /tmp/response; exit 1; }
echo "Politica ILM lebane-logs: retencion ${RETENTION} dias"

code=$(request PUT /_index_template/lebane-logs /setup/lebane-logs-template.json)
[ "$code" = "200" ] || { echo "Error creando la plantilla de indice (HTTP $code)"; cat /tmp/response; exit 1; }
echo "Plantilla de indice lebane-logs aplicada"
