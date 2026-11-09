CREATE TABLE token_seguridad (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tipo VARCHAR(20) NOT NULL,
    hash_token VARCHAR(64) NOT NULL,
    usuario_id BIGINT DEFAULT NULL,
    email VARCHAR(254) NOT NULL,
    organizacion_id BIGINT DEFAULT NULL,
    rol_destino VARCHAR(20) NOT NULL,
    cuenta_id BIGINT NOT NULL,
    expira_en DATETIME(6) NOT NULL,
    usado_en DATETIME(6) DEFAULT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_seguridad_hash (hash_token),
    KEY idx_token_seguridad_usuario (usuario_id),
    KEY idx_token_seguridad_cuenta (cuenta_id),
    KEY idx_token_seguridad_email (email),
    CONSTRAINT chk_token_seguridad_tipo CHECK (tipo IN ('VERIFICACION', 'INVITACION', 'RECUPERACION')),
    CONSTRAINT chk_token_seguridad_rol CHECK (rol_destino IN ('AUDITOR', 'CLIENTE_ADMIN')),
    CONSTRAINT fk_token_seguridad_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_token_seguridad_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_token_seguridad_organizacion
        FOREIGN KEY (organizacion_id) REFERENCES organizacion_cliente (id_empresa)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

