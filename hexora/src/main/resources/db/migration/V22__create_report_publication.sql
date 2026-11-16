CREATE TABLE informe (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    organizacion_id BIGINT NOT NULL,
    proyecto_id BIGINT NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',
    publicado_en DATETIME(6) DEFAULT NULL,
    publicado_por BIGINT DEFAULT NULL,
    hash_contenido CHAR(64) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_informe_public_id (public_id),
    UNIQUE KEY uk_informe_id_cuenta (id, cuenta_id),
    KEY idx_informe_organizacion_cuenta (organizacion_id, cuenta_id),
    KEY idx_informe_proyecto_organizacion_cuenta (proyecto_id, organizacion_id, cuenta_id),
    KEY idx_informe_publicado_por_cuenta (publicado_por, cuenta_id),
    CONSTRAINT fk_informe_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT fk_informe_organizacion_cuenta
        FOREIGN KEY (organizacion_id, cuenta_id)
        REFERENCES organizacion_cliente (id_empresa, cuenta_id),
    CONSTRAINT fk_informe_proyecto_organizacion_cuenta
        FOREIGN KEY (proyecto_id, organizacion_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, organizacion_cliente_id, cuenta_id),
    CONSTRAINT fk_informe_publicado_por_cuenta
        FOREIGN KEY (publicado_por, cuenta_id)
        REFERENCES usuario (id, cuenta_id),
    CONSTRAINT chk_informe_estado CHECK (estado IN ('BORRADOR', 'PUBLICADO')),
    CONSTRAINT chk_informe_publicado_campos CHECK (
        estado <> 'PUBLICADO'
        OR (publicado_en IS NOT NULL AND publicado_por IS NOT NULL AND hash_contenido IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
