-- Esquema inicial de Lebane.
--
-- Convenciones:
--  * IDs bigint generados por secuencias con INCREMENT BY 50 (optimizador "pooled" de Hibernate: inserciones en
--    lote sin un round-trip por fila).
--  * Fechas en timestamptz (UTC).
--  * Las reglas de integridad críticas se garantizan también en la base (CHECK / UNIQUE / FK), no solo en Java.
--  * Los índices específicos del listado (filtros y ordenamientos) se agregan en la Fase 3, junto con las consultas
--    que los usan y su análisis con EXPLAIN.

CREATE SEQUENCE departamento_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE imagen_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE consulta_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE departamento (
    id             bigint         NOT NULL,
    codigo         varchar(20)    NOT NULL,
    titulo         varchar(120)   NOT NULL,
    descripcion    varchar(4000),
    precio         numeric(14, 2) NOT NULL,
    moneda         varchar(3)     NOT NULL,
    ambientes      integer        NOT NULL,
    dormitorios    integer        NOT NULL,
    banos          integer        NOT NULL,
    superficie_m2  numeric(8, 2)  NOT NULL,
    estado         varchar(20)    NOT NULL,
    -- Dirección (embebida: relación 1:1, se filtra sin JOIN)
    calle          varchar(120)   NOT NULL,
    numero         varchar(10)    NOT NULL,
    piso           varchar(10),
    unidad         varchar(10),
    ciudad         varchar(80)    NOT NULL,
    provincia      varchar(80)    NOT NULL,
    codigo_postal  varchar(10),
    latitud        numeric(9, 6),
    longitud       numeric(9, 6),
    place_id       varchar(200),
    -- Auditoría y concurrencia optimista
    version        bigint         NOT NULL DEFAULT 0,
    created_at     timestamptz    NOT NULL,
    updated_at     timestamptz    NOT NULL,

    CONSTRAINT pk_departamento PRIMARY KEY (id),
    CONSTRAINT uk_departamento_codigo UNIQUE (codigo),
    CONSTRAINT ck_departamento_precio CHECK (precio > 0),
    CONSTRAINT ck_departamento_moneda CHECK (moneda IN ('ARS', 'USD')),
    CONSTRAINT ck_departamento_ambientes CHECK (ambientes BETWEEN 1 AND 20),
    CONSTRAINT ck_departamento_dormitorios CHECK (dormitorios >= 0 AND dormitorios < ambientes),
    CONSTRAINT ck_departamento_banos CHECK (banos BETWEEN 1 AND 10),
    CONSTRAINT ck_departamento_superficie CHECK (superficie_m2 > 0),
    CONSTRAINT ck_departamento_estado CHECK (estado IN ('DISPONIBLE', 'RESERVADO', 'VENDIDO')),
    CONSTRAINT ck_departamento_latitud CHECK (latitud IS NULL OR latitud BETWEEN -90 AND 90),
    CONSTRAINT ck_departamento_longitud CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180),
    CONSTRAINT ck_departamento_coordenadas CHECK ((latitud IS NULL) = (longitud IS NULL))
);

CREATE TABLE imagen (
    id               bigint       NOT NULL,
    departamento_id  bigint       NOT NULL,
    object_key       varchar(255) NOT NULL,
    content_type     varchar(100) NOT NULL,
    size_bytes       bigint       NOT NULL,
    -- Orden de la foto; la de menor posición es la imagen principal.
    posicion         integer      NOT NULL,
    created_at       timestamptz  NOT NULL,

    CONSTRAINT pk_imagen PRIMARY KEY (id),
    CONSTRAINT fk_imagen_departamento FOREIGN KEY (departamento_id) REFERENCES departamento (id),
    CONSTRAINT uk_imagen_object_key UNIQUE (object_key),
    -- posicion 0..4 + UNIQUE (departamento_id, posicion): la base garantiza el máximo de 5 fotos por departamento,
    -- incluso ante subidas concurrentes. El índice único también sirve para buscar las imágenes de un departamento
    -- ordenadas y para resolver la imagen principal.
    CONSTRAINT uk_imagen_departamento_posicion UNIQUE (departamento_id, posicion),
    CONSTRAINT ck_imagen_posicion CHECK (posicion BETWEEN 0 AND 4),
    CONSTRAINT ck_imagen_size CHECK (size_bytes > 0)
);

CREATE TABLE consulta (
    id               bigint        NOT NULL,
    departamento_id  bigint        NOT NULL,
    nombre           varchar(100)  NOT NULL,
    email            varchar(254)  NOT NULL,
    telefono         varchar(30),
    mensaje          varchar(2000) NOT NULL,
    created_at       timestamptz   NOT NULL,

    CONSTRAINT pk_consulta PRIMARY KEY (id),
    CONSTRAINT fk_consulta_departamento FOREIGN KEY (departamento_id) REFERENCES departamento (id)
);

-- PostgreSQL no indexa automáticamente las FK: necesario para contar consultas por departamento sin full scan.
CREATE INDEX ix_consulta_departamento ON consulta (departamento_id);
