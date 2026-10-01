-- Índices del listado de departamentos (GET /api/v1/departamentos).
--
-- Cada orden admitido tiene un índice cuyo prefijo coincide con el ORDER BY (incluido el desempate por id), de modo
-- que PostgreSQL recorre el índice en orden y se detiene al llegar al LIMIT, sin ordenar todas las filas filtradas.
-- Los índices btree se recorren en ambos sentidos: sirven para ASC y para DESC.
-- Análisis con EXPLAIN y volumen real: README, sección "Validación de performance".

-- Orden por defecto (created_at DESC, id DESC) sin filtros o con filtros poco selectivos.
CREATE INDEX ix_departamento_created ON departamento (created_at, id);

-- Filtro por estado + orden por fecha.
CREATE INDEX ix_departamento_estado_created ON departamento (estado, created_at, id);

-- Filtro por ciudad sin distinguir mayúsculas (lower(ciudad) = ?) + orden por fecha.
CREATE INDEX ix_departamento_ciudad_created ON departamento (lower(ciudad), created_at, id);

-- Orden por precio (dentro de cada moneda: USD y ARS no se comparan) y rango de precio con moneda.
CREATE INDEX ix_departamento_moneda_precio ON departamento (moneda, precio, id);

-- Orden y rango por superficie.
CREATE INDEX ix_departamento_superficie ON departamento (superficie_m2, id);

-- Búsqueda de texto "contiene" en el título (lower(titulo) LIKE '%texto%'). Un btree no sirve para un patrón con
-- comodín inicial; un índice GIN de trigramas sí, para textos de 3 o más caracteres (mínimo exigido por la API).
-- pg_trgm es una extensión "trusted": la puede crear el dueño de la base sin ser superusuario (PostgreSQL 13+).
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX ix_departamento_titulo_trgm ON departamento USING gin (lower(titulo) gin_trgm_ops);
