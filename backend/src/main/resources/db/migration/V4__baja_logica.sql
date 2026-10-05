-- Baja lógica de departamentos: el registro se conserva (con sus fotos y consultas, como historial) y deja de
-- verse en la API. NULL mientras está publicado.
ALTER TABLE departamento ADD COLUMN fecha_baja timestamptz;
