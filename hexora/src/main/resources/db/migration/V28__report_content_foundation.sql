-- Foundation for generated reports (technical PDF, executive PDF, mitigation compilation, risk matrix).
-- A report row stores an immutable JSON snapshot of the audit content taken at creation time, plus its
-- SHA-256 (hash_contenido, already present). Renderers (PDF/XLSX) read the snapshot, never live tables,
-- so a published report always reproduces exactly what the auditor approved.
-- The informe table is empty at this point (no code path created reports before this migration).

ALTER TABLE informe
    ADD COLUMN titulo VARCHAR(200) NOT NULL DEFAULT '' AFTER tipo,
    ADD COLUMN audiencia VARCHAR(20) NOT NULL DEFAULT 'TECNICO' AFTER titulo,
    ADD COLUMN formato VARCHAR(10) NOT NULL DEFAULT 'PDF' AFTER audiencia,
    ADD COLUMN esquema_version INT NOT NULL DEFAULT 1 AFTER formato,
    ADD COLUMN contenido_json LONGTEXT DEFAULT NULL AFTER hash_contenido,
    ADD COLUMN creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) AFTER contenido_json,
    ADD COLUMN creado_por BIGINT DEFAULT NULL AFTER creado_en,
    ADD KEY idx_informe_creado_por_cuenta (creado_por, cuenta_id),
    ADD KEY idx_informe_proyecto_estado (proyecto_id, cuenta_id, estado, creado_en),
    ADD CONSTRAINT fk_informe_creado_por_cuenta
        FOREIGN KEY (creado_por, cuenta_id)
        REFERENCES usuario (id, cuenta_id),
    ADD CONSTRAINT chk_informe_tipo CHECK (
        tipo IN ('TECNICO_PDF', 'EJECUTIVO_PDF', 'PLANES_MITIGACION_PDF', 'MATRIZ_RIESGO_XLSX')
    ),
    ADD CONSTRAINT chk_informe_audiencia CHECK (audiencia IN ('TECNICO', 'EJECUTIVO')),
    ADD CONSTRAINT chk_informe_formato CHECK (formato IN ('PDF', 'XLSX')),
    ADD CONSTRAINT chk_informe_contenido_json CHECK (contenido_json IS NULL OR JSON_VALID(contenido_json)),
    ADD CONSTRAINT chk_informe_publicado_contenido CHECK (estado <> 'PUBLICADO' OR contenido_json IS NOT NULL);
