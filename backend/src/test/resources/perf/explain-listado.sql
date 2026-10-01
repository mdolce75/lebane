-- EXPLAIN (ANALYZE, BUFFERS) de las consultas del listado, tal como las genera la aplicación (SQL de Hibernate con
-- los parámetros como literales). Requiere psql (usa \gset) y los datos de datos-volumen.sql. Ver README,
-- "Validación de performance", para el procedimiento completo en una base descartable.
\pset pager off
\echo '=== 1. Página por defecto (created_at DESC, id DESC), página 0 de 20'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.precio,d1_0.moneda,d1_0.ambientes,d1_0.dormitorios,d1_0.banos,
       d1_0.superficie_m2,d1_0.estado,d1_0.ciudad,d1_0.provincia,d1_0.created_at
  from departamento d1_0 order by 13 desc,1 desc offset 0 rows fetch first 20 rows only;

\echo '=== 2. COUNT sin filtros'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) select count(d1_0.id) from departamento d1_0;

\echo '=== 3. Página profunda: offset 9900, 100 filas (máximo permitido por la ventana de resultados)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at
  from departamento d1_0 order by d1_0.created_at desc,1 desc offset 9900 rows fetch first 100 rows only;

\echo '=== 4. Filtro estado=VENDIDO: página + COUNT'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at from departamento d1_0 where d1_0.estado='VENDIDO'
 order by d1_0.created_at desc,1 desc offset 0 rows fetch first 20 rows only;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) select count(d1_0.id) from departamento d1_0 where d1_0.estado='VENDIDO';

\echo '=== 5. Filtro ciudad (lower): página + COUNT'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at from departamento d1_0 where lower(d1_0.ciudad)='rosario'
 order by d1_0.created_at desc,1 desc offset 0 rows fetch first 20 rows only;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select count(d1_0.id) from departamento d1_0 where lower(d1_0.ciudad)='rosario';

\echo '=== 6. Rango de precio USD ordenado por precio'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.precio from departamento d1_0
 where d1_0.moneda='USD' and d1_0.precio between 100000 and 200000
 order by d1_0.moneda,d1_0.precio,d1_0.id offset 0 rows fetch first 20 rows only;

\echo '=== 7. Búsqueda de texto (trigramas): página + COUNT'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at from departamento d1_0
 where lower(d1_0.titulo) like '%balcón%' escape '\'
 order by d1_0.created_at desc,1 desc offset 0 rows fetch first 20 rows only;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select count(d1_0.id) from departamento d1_0 where lower(d1_0.titulo) like '%reciclado%' escape '\'
   and lower(d1_0.ciudad)='córdoba';

\echo '=== 8. Agregados de una página de 20 IDs (imagen principal, fotos, consultas)'
SELECT string_agg(id::text, ',') AS ids
  FROM (SELECT id FROM departamento ORDER BY created_at DESC, id DESC LIMIT 20) t \gset
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT d.id, COALESCE(img.total, 0) AS cantidad_imagenes, principal.object_key AS imagen_principal,
       COALESCE(con.total, 0) AS cantidad_consultas
  FROM departamento d
  LEFT JOIN (SELECT departamento_id, COUNT(*) AS total, MIN(posicion) AS primera
               FROM imagen WHERE departamento_id IN (:ids) GROUP BY departamento_id) img ON img.departamento_id = d.id
  LEFT JOIN imagen principal ON principal.departamento_id = d.id AND principal.posicion = img.primera
  LEFT JOIN (SELECT departamento_id, COUNT(*) AS total
               FROM consulta WHERE departamento_id IN (:ids) GROUP BY departamento_id) con ON con.departamento_id = d.id
 WHERE d.id IN (:ids);
