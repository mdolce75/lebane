-- Listado de los departamentos dados de baja (filtro dadosDeBaja=true, para poder reactivarlos). Son pocos: un índice
-- parcial solo con ellos resuelve la página y el COUNT sin recorrer la tabla. El orden por precio o superficie sobre
-- ese subconjunto ordena en memoria las pocas filas que hay.
CREATE INDEX ix_departamento_bajas ON departamento (created_at, id) WHERE fecha_baja IS NOT NULL;
