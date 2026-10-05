-- Búsqueda de texto sin distinguir acentos: "balcon" encuentra "balcón" y al revés.
--
-- unaccent es una extensión "trusted" (la puede crear el dueño de la base sin ser superusuario, PostgreSQL 13+).
-- La función unaccent() es STABLE (depende del diccionario configurado) y un índice solo admite funciones IMMUTABLE:
-- f_unaccent la envuelve fijando el diccionario, que es el patrón recomendado por la documentación de PostgreSQL.
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- El índice de trigramas pasa a ser sobre el título en minúsculas y sin acentos: la consulta usa exactamente esa
-- expresión (f_unaccent(lower(titulo)) LIKE f_unaccent(:patron)). Sigue sin condición de baja, como en V4, para que
-- PostgreSQL conserve las estadísticas de la expresión.
DROP INDEX ix_departamento_titulo_trgm;
CREATE INDEX ix_departamento_titulo_trgm ON departamento USING gin (f_unaccent(lower(titulo)) gin_trgm_ops);
