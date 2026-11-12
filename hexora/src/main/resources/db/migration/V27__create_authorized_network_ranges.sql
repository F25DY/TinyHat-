ALTER TABLE auditoria
    ADD COLUMN monitoreo_continuo_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

INSERT IGNORE INTO capacidad_cuenta (cuenta_id, capacidad, habilitada)
SELECT id, 'monitoreo_continuo', TRUE
FROM cuenta;

ALTER TABLE network_host
    ADD COLUMN proyecto_id BIGINT DEFAULT NULL,
    ADD KEY idx_network_host_proyecto_cuenta (proyecto_id, cuenta_id),
    ADD CONSTRAINT fk_network_host_proyecto_cuenta
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id);

ALTER TABLE network_detected_device
    ADD COLUMN proyecto_id BIGINT DEFAULT NULL,
    ADD KEY idx_network_detected_device_project_account (
        proyecto_id, cuenta_id, last_detected_at),
    ADD CONSTRAINT fk_network_detected_device_project_account
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id),
    DROP INDEX uk_network_detected_device_cuenta_address,
    ADD UNIQUE KEY uk_network_detected_device_project_address (cuenta_id, proyecto_id, address);

ALTER TABLE network_discovery_snapshot
    ADD COLUMN proyecto_id BIGINT DEFAULT NULL,
    ADD KEY idx_network_discovery_snapshot_project_account (
        proyecto_id, cuenta_id, observed_at),
    ADD CONSTRAINT fk_network_discovery_snapshot_project_account
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id);

CREATE TABLE rango_autorizado (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cuenta_id BIGINT NOT NULL,
    proyecto_id BIGINT NOT NULL,
    cidr VARCHAR(18) NOT NULL,
    referencia_autorizacion VARCHAR(255) NOT NULL,
    declarado_por BIGINT NOT NULL,
    declarado_en DATETIME(6) NOT NULL,
    vigente_hasta DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_rango_autorizado_proyecto (cuenta_id, proyecto_id, vigente_hasta),
    CONSTRAINT fk_rango_autorizado_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT fk_rango_autorizado_proyecto_cuenta
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_rango_autorizado_declarante_cuenta
        FOREIGN KEY (declarado_por, cuenta_id)
        REFERENCES usuario (id, cuenta_id),
    CONSTRAINT chk_rango_autorizado_expiracion
        CHECK (vigente_hasta IS NULL OR vigente_hasta > declarado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bloqueo_escaneo (
    proyecto_id BIGINT NOT NULL,
    cuenta_id BIGINT NOT NULL,
    inicio DATETIME(6) NOT NULL,
    usuario_id BIGINT DEFAULT NULL,
    PRIMARY KEY (proyecto_id, cuenta_id),
    CONSTRAINT fk_bloqueo_escaneo_proyecto_cuenta
        FOREIGN KEY (proyecto_id, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_bloqueo_escaneo_usuario_cuenta
        FOREIGN KEY (usuario_id, cuenta_id)
        REFERENCES usuario (id, cuenta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
