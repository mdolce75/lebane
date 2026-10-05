# Colección de Postman

[`lebane.postman_collection.json`](lebane.postman_collection.json) recorre toda la API, en orden. Cada request trae
tests, así que la colección completa funciona como una prueba de punta a punta del backend: 38 requests y 74
verificaciones.

| Carpeta | Qué prueba |
|---|---|
| 0. Salud | Liveness y readiness |
| 1. Listado | Paginado, filtros combinados, orden y errores de validación |
| 2. Alta y detalle | Alta con `Location` y `ETag`, detalle, datos inválidos, alta como vendido, dirección duplicada, 404 |
| 3. Edición con concurrencia optimista | `PUT` con `If-Match` vigente (200) y desactualizado (412) |
| 4. Fotos | Subida a MinIO, archivo que no es imagen, borrado, foto inexistente |
| 5. Consultas | Envío, consulta repetida (409) y contador en el detalle |
| 6. Venta: registro cerrado | Un vendido no se edita, no recibe consultas ni cambia sus fotos |
| 7. Direcciones | Autocompletado y texto demasiado corto |
| 8. Trazabilidad y errores | `X-Request-Id` de ida y vuelta, ruta inexistente y JSON mal formado |
| 9. Baja lógica | 412 con ETag viejo, baja (204), 404 en detalle y segunda baja, fuera del listado, dirección libre |

Las requests comparten variables de la colección: la carpeta 2 crea un departamento y guarda `departamentoId` y
`etag`, y las siguientes los usan. Cada ejecución usa una unidad y un email propios, así que se puede correr las
veces que se quiera sin chocar con datos anteriores.

## Con Postman

1. **Import** → elegir `lebane.postman_collection.json`.
2. Con la aplicación levantada (`docker compose up -d --wait`), **Run collection**.
3. Las requests de fotos adjuntan `foto-ejemplo.png` y `no-es-imagen.png`, que están en esta carpeta. Postman busca
   los archivos en su *working directory* (Settings → General). Si no los encuentra, apuntarlo a esta carpeta o
   elegir el archivo a mano en la pestaña **Body** de esas requests.

La variable `baseUrl` apunta a `http://localhost:8080` (backend directo). Para pasar por nginx, como el frontend,
cambiarla a `http://localhost:3000`.

## Con Newman (línea de comandos)

```bash
npx newman run docs/postman/lebane.postman_collection.json --working-dir docs/postman
```

Para otra URL: `--env-var baseUrl=http://localhost:3000`.
