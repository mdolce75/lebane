-- EXPLAIN (ANALYZE, BUFFERS) de las consultas del listado, tal como las genera la aplicación (SQL de Hibernate con
-- los parámetros como literales). Requiere psql (usa \gset) y los datos de datos-volumen.sql. Ver docs/api.md,
-- "Validación de performance", para el procedimiento completo en una base descartable. Todas filtran
-- fecha_baja is null (baja lógica), igual que la aplicación.
\pset pager off
\echo '=== 1. Página por defecto (created_at DESC, id DESC), página 0 de 20'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.precio,d1_0.moneda,d1_0.ambientes,d1_0.dormitorios,d1_0.banos,
       d1_0.superficie_m2,d1_0.estado,d1_0.ciudad,d1_0.provincia,d1_0.created_at
  from departamento d1_0 where d1_0.fecha_baja is null order by 13 desc,1 desc offset 0 rows fetch first 20 rows only;

\echo '=== 2. COUNT sin filtros'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) select count(d1_0.id) from departamento d1_0 where d1_0.fecha_baja is null;

\echo '=== 3. Página profunda: offset 9900, 100 filas (máximo permitido por la ventana de resultados)'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at
  from departamento d1_0 where d1_0.fecha_baja is null
 order by d1_0.created_at desc,1 desc offset 9900 rows fetch first 100 rows only;

\echo '=== 4. Filtro estado=VENDIDO: página + COUNT'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at from departamento d1_0
 where d1_0.fecha_baja is null and d1_0.estado='VENDIDO'
 order by d1_0.created_at desc,1 desc offset 0 rows fetch first 20 rows only;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) select count(d1_0.id) from departamento d1_0
 where d1_0.fecha_baja is null and d1_0.estado='VENDIDO';

\echo '=== 5. Filtro ciudad (lower): página + COUNT'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at from departamento d1_0
 where d1_0.fecha_baja is null and lower(d1_0.ciudad)='rosario'
 order by d1_0.created_at desc,1 desc offset 0 rows fetch first 20 rows only;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select count(d1_0.id) from departamento d1_0 where d1_0.fecha_baja is null and lower(d1_0.ciudad)='rosario';

\echo '=== 6. Rango de precio USD ordenado por precio'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.precio from departamento d1_0
 where d1_0.fecha_baja is null and d1_0.moneda='USD' and d1_0.precio between 100000 and 200000
 order by d1_0.moneda,d1_0.precio,d1_0.id offset 0 rows fetch first 20 rows only;

\echo '=== 7. Búsqueda de texto (trigramas): página + COUNT'
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,d1_0.codigo,d1_0.titulo,d1_0.created_at from departamento d1_0
 where d1_0.fecha_baja is null and lower(d1_0.titulo) like '%balcón%' escape '\'
 order by d1_0.created_at desc,1 desc offset 0 rows fetch first 20 rows only;
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select count(d1_0.id) from departamento d1_0
 where d1_0.fecha_baja is null and lower(d1_0.titulo) like '%reciclado%' escape '\'
   and lower(d1_0.ciudad)='córdoba';

\echo '=== 8. Agregados de una página de 20 IDs (imagen principal, fotos, consultas)'
SELECT string_agg(id::text, ',') AS ids
  FROM (SELECT id FROM departamento WHERE fecha_baja IS NULL ORDER BY created_at DESC, id DESC LIMIT 20) t \gset
-- Generada por la Criteria API (DepartamentoListadoRepositoryImpl#agregados): subconsultas escalares correlacionadas.
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
select d1_0.id,
       (select count(i1_0.posicion) from imagen i1_0 where i1_0.departamento_id=d1_0.id),
       (select i2_0.object_key from imagen i2_0 where i2_0.departamento_id=d1_0.id
           and i2_0.posicion=(select min(i3_0.posicion) from imagen i3_0 where i3_0.departamento_id=d1_0.id)),
       (select count(c1_0.departamento_id) from consulta c1_0 where c1_0.departamento_id=d1_0.id)
  from departamento d1_0 where d1_0.id in (:ids);
