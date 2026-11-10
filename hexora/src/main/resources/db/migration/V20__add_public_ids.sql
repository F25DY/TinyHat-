UPDATE cuenta
SET public_id = UUID()
WHERE public_id IS NULL;

ALTER TABLE cuenta
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_cuenta_public_id (public_id);

UPDATE organizacion_cliente
SET public_id = UUID()
WHERE public_id IS NULL;

ALTER TABLE organizacion_cliente
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_organizacion_public_id (public_id);

ALTER TABLE auditoria
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE auditoria SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE auditoria
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_auditoria_public_id (public_id);

ALTER TABLE area_auditable
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE area_auditable SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE area_auditable
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_area_public_id (public_id);

ALTER TABLE vulnerabilidad
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE vulnerabilidad SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE vulnerabilidad
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_vulnerabilidad_public_id (public_id);

ALTER TABLE usuario
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE usuario SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE usuario
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_usuario_public_id (public_id);

ALTER TABLE network_host
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_host SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_host
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_host_public_id (public_id);

ALTER TABLE network_scan
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_scan SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_scan
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_scan_public_id (public_id);

ALTER TABLE port_scan_result
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE port_scan_result SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE port_scan_result
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_port_scan_result_public_id (public_id);

ALTER TABLE network_event
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_event SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_event
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_event_public_id (public_id);

ALTER TABLE monitored_port
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE monitored_port SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE monitored_port
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_monitored_port_public_id (public_id);

ALTER TABLE network_detected_device
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_detected_device SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_detected_device
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_detected_device_public_id (public_id);

ALTER TABLE network_discovery_event
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_discovery_event SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_discovery_event
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_discovery_event_public_id (public_id);

ALTER TABLE network_discovery_snapshot
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_discovery_snapshot SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_discovery_snapshot
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_discovery_snapshot_public_id (public_id);

ALTER TABLE network_discovery_snapshot_device
    ADD COLUMN public_id CHAR(36) DEFAULT NULL;
UPDATE network_discovery_snapshot_device SET public_id = UUID() WHERE public_id IS NULL;
ALTER TABLE network_discovery_snapshot_device
    MODIFY COLUMN public_id CHAR(36) NOT NULL,
    ADD UNIQUE KEY uk_network_discovery_snapshot_device_public_id (public_id);
