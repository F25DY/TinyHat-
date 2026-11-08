CREATE TABLE cuenta (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) DEFAULT NULL,
    nombre VARCHAR(150) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    creado_en DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cuenta_id_cuenta (id),
    CONSTRAINT chk_cuenta_tipo CHECK (tipo IN ('AUDITOR')),
    CONSTRAINT chk_cuenta_estado CHECK (estado IN ('ACTIVA', 'SUSPENDIDA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO cuenta (id, nombre, tipo, estado, creado_en)
VALUES (1, 'Cuenta inicial Hexora', 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6));

RENAME TABLE empresa TO organizacion_cliente;

ALTER TABLE organizacion_cliente
    ADD COLUMN public_id CHAR(36) DEFAULT NULL,
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_organizacion_id_cuenta (id_empresa, cuenta_id),
    ADD KEY idx_organizacion_cuenta (cuenta_id),
    ADD CONSTRAINT fk_organizacion_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id);

SET @auditoria_empresa_fk = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.key_column_usage
    WHERE constraint_schema = DATABASE()
      AND table_name = 'auditoria'
      AND column_name = 'empresa_id'
      AND referenced_table_name = 'organizacion_cliente'
    LIMIT 1
);
SET @drop_auditoria_empresa_fk = IF(
    @auditoria_empresa_fk IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE auditoria DROP FOREIGN KEY `', REPLACE(@auditoria_empresa_fk, '`', '``'), '`')
);
PREPARE drop_auditoria_empresa_fk FROM @drop_auditoria_empresa_fk;
EXECUTE drop_auditoria_empresa_fk;
DEALLOCATE PREPARE drop_auditoria_empresa_fk;

ALTER TABLE auditoria
    CHANGE COLUMN empresa_id organizacion_cliente_id BIGINT NOT NULL,
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_auditoria_id_cuenta (id_auditoria, cuenta_id),
    ADD UNIQUE KEY uk_auditoria_id_organizacion_cuenta (id_auditoria, organizacion_cliente_id, cuenta_id),
    ADD KEY idx_auditoria_cuenta (cuenta_id),
    ADD CONSTRAINT fk_auditoria_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    ADD CONSTRAINT fk_auditoria_organizacion_cuenta
        FOREIGN KEY (organizacion_cliente_id, cuenta_id)
        REFERENCES organizacion_cliente (id_empresa, cuenta_id);

ALTER TABLE area_auditable
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_area_id_cuenta (id_area, cuenta_id),
    ADD KEY idx_area_cuenta (cuenta_id),
    ADD CONSTRAINT fk_area_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_area_auditable_auditoria,
    ADD CONSTRAINT fk_area_auditoria_cuenta
        FOREIGN KEY (id_auditoria, cuenta_id)
        REFERENCES auditoria (id_auditoria, cuenta_id);

ALTER TABLE vulnerabilidad
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_vulnerabilidad_id_cuenta (id, cuenta_id),
    ADD KEY idx_vulnerabilidad_cuenta (cuenta_id),
    ADD CONSTRAINT fk_vulnerabilidad_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_vulnerabilidad_area,
    ADD CONSTRAINT fk_vulnerabilidad_area_cuenta
        FOREIGN KEY (id_area, cuenta_id)
        REFERENCES area_auditable (id_area, cuenta_id);

ALTER TABLE network_host
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_network_host_id_cuenta (id, cuenta_id),
    ADD KEY idx_network_host_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_host_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP INDEX uk_network_host_address,
    ADD UNIQUE KEY uk_network_host_cuenta_address (cuenta_id, address);

ALTER TABLE network_scan
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_network_scan_id_host_cuenta (id, host_id, cuenta_id),
    ADD KEY idx_network_scan_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_scan_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_network_scan_host,
    ADD CONSTRAINT fk_network_scan_host_cuenta
        FOREIGN KEY (host_id, cuenta_id)
        REFERENCES network_host (id, cuenta_id);

ALTER TABLE port_scan_result
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD KEY idx_port_scan_result_cuenta (cuenta_id),
    ADD CONSTRAINT fk_port_scan_result_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_port_scan_result_scan,
    DROP FOREIGN KEY fk_port_scan_result_host,
    ADD CONSTRAINT fk_port_scan_result_scan_host_cuenta
        FOREIGN KEY (network_scan_id, network_host_id, cuenta_id)
        REFERENCES network_scan (id, host_id, cuenta_id),
    ADD CONSTRAINT fk_port_scan_result_host_cuenta
        FOREIGN KEY (network_host_id, cuenta_id)
        REFERENCES network_host (id, cuenta_id);

ALTER TABLE network_event
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD KEY idx_network_event_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_event_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_network_event_host,
    DROP FOREIGN KEY fk_network_event_scan,
    ADD CONSTRAINT fk_network_event_host_cuenta
        FOREIGN KEY (network_host_id, cuenta_id)
        REFERENCES network_host (id, cuenta_id),
    ADD CONSTRAINT fk_network_event_scan_host_cuenta
        FOREIGN KEY (network_scan_id, network_host_id, cuenta_id)
        REFERENCES network_scan (id, host_id, cuenta_id);

ALTER TABLE monitored_port
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD KEY idx_monitored_port_cuenta (cuenta_id),
    ADD CONSTRAINT fk_monitored_port_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP INDEX uk_monitored_port_host_port,
    ADD UNIQUE KEY uk_monitored_port_cuenta_host_port (cuenta_id, network_host_id, port),
    DROP FOREIGN KEY fk_monitored_port_host,
    ADD CONSTRAINT fk_monitored_port_host_cuenta
        FOREIGN KEY (network_host_id, cuenta_id)
        REFERENCES network_host (id, cuenta_id);

ALTER TABLE network_detected_device
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_network_detected_device_id_cuenta (id, cuenta_id),
    ADD KEY idx_network_detected_device_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_detected_device_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP INDEX uk_network_detected_device_address,
    ADD UNIQUE KEY uk_network_detected_device_cuenta_address (cuenta_id, address);

ALTER TABLE network_discovery_event
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD KEY idx_network_discovery_event_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_discovery_event_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_network_discovery_event_device,
    ADD CONSTRAINT fk_network_discovery_event_device_cuenta
        FOREIGN KEY (detected_device_id, cuenta_id)
        REFERENCES network_detected_device (id, cuenta_id);

ALTER TABLE network_discovery_snapshot
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uk_network_discovery_snapshot_id_cuenta (id, cuenta_id),
    ADD KEY idx_network_discovery_snapshot_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_discovery_snapshot_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id);

ALTER TABLE network_discovery_snapshot_device
    ADD COLUMN cuenta_id BIGINT NOT NULL DEFAULT 1,
    ADD KEY idx_network_discovery_snapshot_device_cuenta (cuenta_id),
    ADD CONSTRAINT fk_network_discovery_snapshot_device_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    DROP FOREIGN KEY fk_network_discovery_snapshot_device_snapshot,
    ADD CONSTRAINT fk_network_discovery_snapshot_device_snapshot_cuenta
        FOREIGN KEY (snapshot_id, cuenta_id)
        REFERENCES network_discovery_snapshot (id, cuenta_id)
        ON DELETE CASCADE;
