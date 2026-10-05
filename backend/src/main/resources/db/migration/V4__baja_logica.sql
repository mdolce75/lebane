-- Baja lógica de departamentos: el registro se conserva (con sus fotos y consultas, como historial) y deja de
-- verse en la API. NULL mientras está publicado.
ALTER TABLE departamento ADD COLUMN fecha_baja timestamptz;

-- El listado y la regla de avisos duplicados solo consultan departamentos vigentes (fecha_baja IS NULL). Sus índices
-- pasan a ser parciales con esa misma condición:
--  * el planificador los sigue usando, porque la consulta incluye el predicado del índice;
--  * el COUNT y los filtros siguen resolviéndose solo con el índice (index-only scan), sin leer la tabla para
--    descartar los dados de baja;
--  * los dados de baja no ocupan lugar en ellos.
-- Mismos nombres y columnas que en V2/V3.

-- COUNT del listado sin filtros (count(id) ... WHERE fecha_baja IS NULL): antes lo resolvía un index-only scan sobre
-- la PK, que no sabe de bajas. Este índice es el equivalente para los vigentes, igual de chico.
CREATE INDEX ix_departamento_vigentes ON departamento (id) WHERE fecha_baja IS NULL;

DROP INDEX ix_departamento_created;
CREATE INDEX ix_departamento_created ON departamento (created_at, id) WHERE fecha_baja IS NULL;

DROP INDEX ix_departamento_estado_created;
CREATE INDEX ix_departamento_estado_created ON departamento (estado, created_at, id) WHERE fecha_baja IS NULL;

DROP INDEX ix_departamento_ciudad_created;
CREATE INDEX ix_departamento_ciudad_created ON departamento (lower(ciudad), created_at, id) WHERE fecha_baja IS NULL;

DROP INDEX ix_departamento_moneda_precio;
CREATE INDEX ix_departamento_moneda_precio ON departamento (moneda, precio, id) WHERE fecha_baja IS NULL;

DROP INDEX ix_departamento_superficie;
CREATE INDEX ix_departamento_superficie ON departamento (superficie_m2, id) WHERE fecha_baja IS NULL;

-- ix_departamento_titulo_trgm queda como está, sin condición: PostgreSQL toma de ese índice las estadísticas de
-- lower(titulo). Como índice parcial dejaba de usarlas, estimaba 10 filas en lugar de miles para "contiene" y elegía
-- un plan peor (página de búsqueda: de 0,1 ms a 27 ms con 100.000 departamentos). Un GIN no permite index-only scan,
-- así que la condición no le aportaba nada.

DROP INDEX ix_departamento_direccion;
CREATE INDEX ix_departamento_direccion ON departamento (lower(calle), lower(numero), lower(ciudad))
    WHERE fecha_baja IS NULL;
