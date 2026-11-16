CREATE TABLE usuario (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cuenta_id BIGINT NOT NULL,
    organizacion_id BIGINT DEFAULT NULL,
    rol VARCHAR(20) NOT NULL,
    estado VARCHAR(30) NOT NULL,
    email_normalizado VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_email_normalizado (email_normalizado),
    UNIQUE KEY uk_usuario_id_cuenta (id, cuenta_id),
    KEY idx_usuario_organizacion_cuenta (organizacion_id, cuenta_id),
    CONSTRAINT fk_usuario_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT fk_usuario_organizacion_cuenta
        FOREIGN KEY (organizacion_id, cuenta_id)
        REFERENCES organizacion_cliente (id_empresa, cuenta_id),
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('AUDITOR', 'CLIENTE_ADMIN')),
    CONSTRAINT chk_usuario_estado CHECK (
        estado IN ('PENDIENTE_VERIFICACION', 'INVITADO', 'ACTIVO', 'SUSPENDIDO')
    ),
    CONSTRAINT chk_usuario_cliente_admin_org CHECK (
        rol <> 'CLIENTE_ADMIN' OR organizacion_id IS NOT NULL
    ),
    CONSTRAINT chk_usuario_email_normalizado CHECK (
        email_normalizado = LOWER(TRIM(email_normalizado))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asignacion_auditor_proyecto (
    usuario_id BIGINT NOT NULL,
    proyecto_id BIGINT NOT NULL,
    cuenta_id BIGINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (usuario_id, proyecto_id, cuenta_id),
    KEY idx_asignacion_proyecto_cuenta (proyecto_id, cuenta_id),
    CONSTRAINT fk_asignacion_auditor_usuario_cuenta
        FOREIGN KEY (usuario_id, cuenta_id)
        REFERENCES usuario (id, cuenta_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_asignacion_auditor_proyecto_cuenta
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
