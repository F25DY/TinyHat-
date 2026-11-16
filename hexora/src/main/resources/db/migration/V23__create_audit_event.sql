CREATE TABLE audit_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ts DATETIME(6) NOT NULL,
    evento VARCHAR(80) NOT NULL,
    usuario_id BIGINT DEFAULT NULL,
    cuenta_id BIGINT NOT NULL,
    organizacion_id BIGINT DEFAULT NULL,
    ip VARCHAR(45) DEFAULT NULL,
    user_agent VARCHAR(512) DEFAULT NULL,
    recurso_tipo VARCHAR(80) DEFAULT NULL,
    recurso_public_id CHAR(36) DEFAULT NULL,
    resultado VARCHAR(30) NOT NULL,
    detalle JSON DEFAULT NULL,
    correlation_id VARCHAR(64) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_event_ts (ts),
    KEY idx_audit_event_cuenta_ts (cuenta_id, ts),
    KEY idx_audit_event_evento_ts (evento, ts),
    CONSTRAINT fk_audit_event_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT fk_audit_event_usuario_cuenta
        FOREIGN KEY (usuario_id, cuenta_id)
        REFERENCES usuario (id, cuenta_id),
    CONSTRAINT fk_audit_event_organizacion_cuenta
        FOREIGN KEY (organizacion_id, cuenta_id)
        REFERENCES organizacion_cliente (id_empresa, cuenta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
