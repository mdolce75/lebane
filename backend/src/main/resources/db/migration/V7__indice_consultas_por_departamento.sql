-- Listado de las consultas de un departamento, de la más reciente a la más antigua (paginado).
-- El índice compuesto resuelve el filtro por departamento y el orden sin sort en memoria; su prefijo
-- (departamento_id) sigue sirviendo para el COUNT del listado y del detalle, así que reemplaza al índice simple.
CREATE INDEX ix_consulta_departamento_fecha ON consulta (departamento_id, created_at DESC, id DESC);
DROP INDEX ix_consulta_departamento;
