#!/bin/sh
# Crea (o actualiza) el data view "Lebane logs" en Kibana (idempotente; servicio "kibana-setup" del perfil
# observability). Credenciales solo por variables de entorno; nunca se imprimen.
set -eu

KIBANA="http://kibana:5601"
AUTH="elastic:${ELASTIC_PASSWORD}"

until [ "$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" "$KIBANA/api/status")" = "200" ]; do
  echo "Esperando a Kibana..."
  sleep 5
done

code=$(curl -s -o /tmp/response -w '%{http_code}' -u "$AUTH" -X POST "$KIBANA/api/data_views/data_view" \
  -H 'Content-Type: application/json' -H 'kbn-xsrf: lebane-setup' \
  -d '{"override":true,"data_view":{"id":"lebane-logs","name":"Lebane logs","title":"lebane-logs-*","timeFieldName":"@timestamp"}}')
[ "$code" = "200" ] || { echo "Error creando el data view (HTTP $code)"; cat /tmp/response; exit 1; }
echo "Data view 'Lebane logs' (lebane-logs-*) listo en Kibana > Discover"
