-- Acceso del administrador de la MiPyme (CLIENTE_ADMIN) a un proyecto concreto.
-- El AUDITOR lo agrega al proyecto con una contraseña temporal que le entrega; el cliente debe cambiarla al iniciar
-- sesion. Puede entrar mientras tenga al menos un proyecto asignado y activo (no "Completado"). Retirar el acceso no
-- borra la fila: queda `retirado_en` como historia y el usuario deja de poder entrar por ese proyecto.

ALTER TABLE usuario
    ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE asignacion_cliente_proyecto (
    usuario_id BIGINT NOT NULL,
    proyecto_id BIGINT NOT NULL,
    cuenta_id BIGINT NOT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    creado_por BIGINT DEFAULT NULL,
    retirado_en DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (usuario_id, proyecto_id, cuenta_id),
    KEY idx_asignacion_cliente_proyecto_cuenta (proyecto_id, cuenta_id),
    CONSTRAINT fk_asignacion_cliente_usuario_cuenta
        FOREIGN KEY (usuario_id, cuenta_id)
        REFERENCES usuario (id, cuenta_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_asignacion_cliente_proyecto_cuenta
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
