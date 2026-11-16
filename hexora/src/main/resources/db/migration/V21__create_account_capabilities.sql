CREATE TABLE capacidad_cuenta (
    cuenta_id BIGINT NOT NULL,
    capacidad VARCHAR(40) NOT NULL,
    habilitada BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (cuenta_id, capacidad),
    CONSTRAINT fk_capacidad_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_capacidad_cuenta_nombre CHECK (
        capacidad IN ('modulo_red', 'informes_pdf', 'monitoreo_continuo')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO capacidad_cuenta (cuenta_id, capacidad, habilitada)
SELECT id, 'modulo_red', TRUE FROM cuenta
UNION ALL
SELECT id, 'informes_pdf', TRUE FROM cuenta
UNION ALL
SELECT id, 'monitoreo_continuo', TRUE FROM cuenta;
