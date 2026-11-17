-- Modelo de informes: serie (informe) -> versiones -> archivos publicados.
-- Sustituye la base de V22/V28 (un registro por informe, sin versiones ni archivos). No hay datos que conservar.
-- Mismo patrón de aislamiento que el resto del esquema: cuenta_id en cada tabla y claves foráneas compuestas, de modo
-- que ninguna fila pueda apuntar a un proyecto, usuario o informe de otra cuenta u organización.

DROP TABLE IF EXISTS informe;

-- Una serie por proyecto y tipo (MR, TEC, EJE, MIT, FIN).
CREATE TABLE informe (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    organizacion_id BIGINT NOT NULL,
    proyecto_id BIGINT NOT NULL,
    tipo VARCHAR(3) NOT NULL,
    alcance VARCHAR(16) NOT NULL DEFAULT 'PROYECTO',
    anio SMALLINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    creado_por BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_informe_public_id (public_id),
    UNIQUE KEY uk_informe_id_cuenta (id, cuenta_id),
    UNIQUE KEY uk_informe_proyecto_tipo (proyecto_id, tipo),
    KEY idx_informe_organizacion_cuenta (organizacion_id, cuenta_id),
    KEY idx_informe_creado_por_cuenta (creado_por, cuenta_id),
    CONSTRAINT fk_informe_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT fk_informe_proyecto_organizacion_cuenta
        FOREIGN KEY (proyecto_id, organizacion_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, organizacion_cliente_id, cuenta_id),
    CONSTRAINT fk_informe_creado_por_cuenta
        FOREIGN KEY (creado_por, cuenta_id)
        REFERENCES usuario (id, cuenta_id),
    CONSTRAINT chk_informe_tipo CHECK (tipo IN ('MR', 'TEC', 'EJE', 'MIT', 'FIN')),
    CONSTRAINT chk_informe_alcance CHECK (alcance IN ('PROYECTO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Una versión de una serie. Un publicado nunca se edita: solo pasa a DESPUBLICADO (con motivo).
CREATE TABLE informe_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    informe_id BIGINT NOT NULL,
    numero INT NOT NULL,
    estado VARCHAR(16) NOT NULL DEFAULT 'BORRADOR',
    narrativa_json JSON NOT NULL,
    snapshot_json JSON DEFAULT NULL,
    snapshot_sha256 CHAR(64) DEFAULT NULL,
    plantilla_version VARCHAR(16) DEFAULT NULL,
    autor_id BIGINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    publicado_en DATETIME(6) DEFAULT NULL,
    despublicado_en DATETIME(6) DEFAULT NULL,
    motivo_despublicacion VARCHAR(500) DEFAULT NULL,
    version_bloqueo INT NOT NULL DEFAULT 0,
    -- I1 e I2 en la base: como máximo un BORRADOR y un PUBLICADO por serie (los NULL no chocan entre sí).
    borrador_unico BIGINT GENERATED ALWAYS AS (CASE WHEN estado = 'BORRADOR' THEN informe_id END) VIRTUAL,
    publicado_unico BIGINT GENERATED ALWAYS AS (CASE WHEN estado = 'PUBLICADO' THEN informe_id END) VIRTUAL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_informe_version_public_id (public_id),
    UNIQUE KEY uk_informe_version_id_cuenta (id, cuenta_id),
    UNIQUE KEY uk_informe_version_numero (informe_id, numero),
    UNIQUE KEY uk_informe_version_un_borrador (borrador_unico),
    UNIQUE KEY uk_informe_version_un_publicado (publicado_unico),
    KEY idx_informe_version_estado (cuenta_id, estado, publicado_en),
    KEY idx_informe_version_autor_cuenta (autor_id, cuenta_id),
    CONSTRAINT fk_informe_version_informe_cuenta
        FOREIGN KEY (informe_id, cuenta_id)
        REFERENCES informe (id, cuenta_id),
    CONSTRAINT fk_informe_version_autor_cuenta
        FOREIGN KEY (autor_id, cuenta_id)
        REFERENCES usuario (id, cuenta_id),
    CONSTRAINT chk_informe_version_estado CHECK (estado IN ('BORRADOR', 'PUBLICADO', 'DESPUBLICADO')),
    CONSTRAINT chk_informe_version_numero CHECK (numero >= 1),
    CONSTRAINT chk_informe_version_sha256 CHECK (snapshot_sha256 IS NULL OR CHAR_LENGTH(snapshot_sha256) = 64),
    -- El snapshot y su hash existen solo desde la publicación (el borrador usa datos vivos).
    CONSTRAINT chk_informe_version_borrador CHECK (
        estado <> 'BORRADOR'
        OR (snapshot_json IS NULL AND snapshot_sha256 IS NULL AND plantilla_version IS NULL
            AND publicado_en IS NULL AND despublicado_en IS NULL AND motivo_despublicacion IS NULL)
    ),
    CONSTRAINT chk_informe_version_publicado CHECK (
        estado <> 'PUBLICADO'
        OR (snapshot_json IS NOT NULL AND snapshot_sha256 IS NOT NULL AND plantilla_version IS NOT NULL
            AND publicado_en IS NOT NULL AND despublicado_en IS NULL AND motivo_despublicacion IS NULL)
    ),
    CONSTRAINT chk_informe_version_despublicado CHECK (
        estado <> 'DESPUBLICADO'
        OR (snapshot_json IS NOT NULL AND snapshot_sha256 IS NOT NULL AND plantilla_version IS NOT NULL
            AND publicado_en IS NOT NULL AND despublicado_en IS NOT NULL AND motivo_despublicacion IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Archivo generado al publicar (I4: solo existe para versiones publicadas o despublicadas, lo valida el servicio).
-- La base guarda solo la ruta relativa y el SHA-256; el archivo vive fuera de las rutas servidas.
CREATE TABLE informe_archivo (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    formato VARCHAR(4) NOT NULL,
    ruta_relativa VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    bytes BIGINT NOT NULL,
    mime VARCHAR(100) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_informe_archivo_public_id (public_id),
    UNIQUE KEY uk_informe_archivo_version_formato (version_id, formato),
    KEY idx_informe_archivo_cuenta (cuenta_id, version_id),
    CONSTRAINT fk_informe_archivo_version_cuenta
        FOREIGN KEY (version_id, cuenta_id)
        REFERENCES informe_version (id, cuenta_id),
    CONSTRAINT chk_informe_archivo_formato CHECK (formato IN ('PDF', 'DOCX', 'XLSX')),
    CONSTRAINT chk_informe_archivo_sha256 CHECK (CHAR_LENGTH(sha256) = 64),
    CONSTRAINT chk_informe_archivo_bytes CHECK (bytes >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
