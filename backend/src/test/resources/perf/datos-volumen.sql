-- Datos sintéticos de volumen para validar la performance del listado (ListadoPerformanceIT y análisis manual con
-- EXPLAIN). Genera, dentro de PostgreSQL y en segundos:
--   * 100.000 departamentos (códigos VOL-0000001…), 8 ciudades, 3 estados, ARS/USD, fechas distintas;
--   * ~200.000 imágenes (2 de cada 3 departamentos tienen entre 1 y 5);
--   * ~300.000 consultas (entre 0 y 6 por departamento).
-- Idempotente: si ya hay datos VOL-, no hace nada. Usa las secuencias de la aplicación (sin colisión de IDs).
--
-- Uso manual (stack Docker levantado):
--   docker compose exec -T postgres psql -U lebane -d lebane < backend/src/test/resources/perf/datos-volumen.sql
--   docker compose exec -T postgres psql -U lebane -d lebane -c "VACUUM ANALYZE departamento, imagen, consulta"
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM departamento WHERE codigo = 'VOL-0000001') THEN
        RAISE NOTICE 'Datos de volumen ya cargados';
        RETURN;
    END IF;

    INSERT INTO departamento (id, codigo, titulo, precio, moneda, ambientes, dormitorios, banos, superficie_m2,
                              estado, calle, numero, ciudad, provincia, version, created_at, updated_at)
    SELECT nextval('departamento_seq'),
           'VOL-' || lpad(g::text, 7, '0'),
           (ARRAY['Luminoso', 'Amplio', 'Reciclado', 'A estrenar', 'Clásico', 'Moderno'])[1 + g % 6]
               || ' ' || (1 + g % 5) || ' ambientes '
               || (ARRAY['con balcón', 'con patio', 'con cochera', 'al frente', 'contrafrente',
                         'con vista abierta'])[1 + (g / 6) % 6],
           CASE WHEN g % 4 = 0 THEN 30000000 + (g::bigint * 7919) % 270000000
                ELSE 50000 + (g::bigint * 7919) % 450000 END,
           CASE WHEN g % 4 = 0 THEN 'ARS' ELSE 'USD' END,
           1 + g % 5,
           g % 5,
           1 + g % 2,
           25 + (g * 31) % 175,
           CASE WHEN g % 10 < 6 THEN 'DISPONIBLE' WHEN g % 10 < 8 THEN 'RESERVADO' ELSE 'VENDIDO' END,
           'Calle ' || (g % 500),
           (1 + g % 9000)::text,
           c.ciudad,
           c.provincia,
           0,
           now() - make_interval(mins => g),
           now() - make_interval(mins => g)
      FROM generate_series(1, 100000) AS g
     CROSS JOIN LATERAL (
           SELECT (ARRAY['Ciudad Autónoma de Buenos Aires', 'Rosario', 'Córdoba', 'Mendoza', 'Mar del Plata',
                         'La Plata', 'San Miguel de Tucumán', 'Salta'])[1 + g % 8] AS ciudad,
                  (ARRAY['CABA', 'Santa Fe', 'Córdoba', 'Mendoza', 'Buenos Aires', 'Buenos Aires', 'Tucumán',
                         'Salta'])[1 + g % 8] AS provincia) AS c;

    INSERT INTO imagen (id, departamento_id, object_key, content_type, size_bytes, posicion, created_at)
    SELECT nextval('imagen_seq'), d.id, 'volumen/' || d.id || '/' || p || '.jpg', 'image/jpeg', 150000, p,
           d.created_at
      FROM departamento d
     CROSS JOIN LATERAL (SELECT substr(d.codigo, 5)::int AS g) AS n
     CROSS JOIN LATERAL generate_series(0, n.g % 5) AS p
     WHERE d.codigo LIKE 'VOL-%' AND n.g % 3 <> 0;

    INSERT INTO consulta (id, departamento_id, nombre, email, mensaje, created_at)
    SELECT nextval('consulta_seq'), d.id, 'Interesado ' || c, 'interesado' || c || '@example.com',
           'Consulta generada para pruebas de volumen.', d.created_at + make_interval(mins => c)
      FROM departamento d
     CROSS JOIN LATERAL (SELECT substr(d.codigo, 5)::int AS g) AS n
     CROSS JOIN LATERAL generate_series(1, n.g % 7) AS c
     WHERE d.codigo LIKE 'VOL-%';
END
$$;
