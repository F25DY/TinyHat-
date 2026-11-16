CREATE TABLE mfa_credencial (
    usuario_id BIGINT NOT NULL,
    secreto_cifrado VARBINARY(64) NOT NULL,
    nonce BINARY(12) NOT NULL,
    ultimo_paso_usado BIGINT DEFAULT NULL,
    activo BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usuario_id),
    CONSTRAINT fk_mfa_credencial_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mfa_codigo_recuperacion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    hash VARCHAR(100) NOT NULL,
    usado_en DATETIME(6) DEFAULT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_mfa_codigo_recuperacion_usuario (usuario_id, usado_en),
    CONSTRAINT fk_mfa_codigo_recuperacion_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
