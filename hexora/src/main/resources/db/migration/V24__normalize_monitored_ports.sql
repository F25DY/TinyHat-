INSERT IGNORE INTO monitored_port (
    network_host_id,
    port,
    enabled,
    cuenta_id,
    public_id
)
SELECT
    host.id,
    configured.port,
    TRUE,
    host.cuenta_id,
    UUID()
FROM network_host host
JOIN JSON_TABLE(
    CONCAT('[', host.monitored_ports, ']'),
    '$[*]' COLUMNS (port INT PATH '$')
) configured
LEFT JOIN monitored_port existing
    ON existing.cuenta_id = host.cuenta_id
    AND existing.network_host_id = host.id
    AND existing.port = configured.port
WHERE existing.id IS NULL;

ALTER TABLE network_host
    DROP COLUMN monitored_ports;
