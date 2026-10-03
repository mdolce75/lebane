-- Índices de las reglas de duplicados (se controlan en los servicios).

-- Avisos duplicados: ¿hay un departamento publicado en la misma dirección? (lower(calle) = ? AND lower(numero) = ?
-- AND lower(ciudad) = ? ...). Calle, número y ciudad ya dejan muy pocas filas; piso, unidad y estado se filtran ahí.
-- No es UNIQUE: una base existente puede tener duplicados cargados antes de la regla y la migración fallaría.
CREATE INDEX ix_departamento_direccion ON departamento (lower(calle), lower(numero), lower(ciudad));

-- Consultas duplicadas: ¿el mismo email consultó por el departamento en las últimas 24 horas? Spring Data compara
-- sin mayúsculas con upper(); el índice usa la misma expresión para poder resolverlo.
CREATE INDEX ix_consulta_departamento_email ON consulta (departamento_id, upper(email), created_at);
