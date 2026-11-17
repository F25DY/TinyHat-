-- Nivel de riesgo alineado con NIST (SP 800-30 Rev. 1, Tabla I-2; FIPS 199, regla del punto más alto).
-- Sustituye el cálculo de V30 (tramos de 2 y cuatro niveles). Es autosuficiente: recalcula desde C/I/D y probabilidad.
--
--   impacto        = dimensión C/I/D más alta (0-10)
--   nivel (0-10)   = 0 -> 1 | 1-2 -> 2 | 3-7 -> 3 | 8-9 -> 4 | 10 -> 5   (cortes de NIST escalados a 0-10)
--   nivel_riesgo   = tabla probabilidad x impacto (5 niveles de riesgo)
--
-- La regla es la de com.hexora.hexora.riesgo.EscalaRiesgo y la de vulnerability-form.js;
-- EscalaRiesgoMigracionIntegrationTest comprueba que el SQL y Java coinciden en todas las combinaciones.

UPDATE vulnerabilidad
SET impacto = GREATEST(0, LEAST(10, GREATEST(confidencialidad, integridad, disponibilidad)));

-- Cada fila de ELT es una probabilidad (1..5); cada carácter, un impacto (1..5):
-- V = Muy bajo, B = Bajo, M = Medio, A = Alto, C = Critico.
UPDATE vulnerabilidad
SET nivel_riesgo = CASE SUBSTRING(
        ELT(
            CASE WHEN probabilidad <= 0 THEN 1 WHEN probabilidad <= 2 THEN 2 WHEN probabilidad <= 7 THEN 3
                 WHEN probabilidad <= 9 THEN 4 ELSE 5 END,
            'VVVBB', 'VBBBM', 'VBMMA', 'VBMAC', 'VBMAC'),
        CASE WHEN impacto <= 0 THEN 1 WHEN impacto <= 2 THEN 2 WHEN impacto <= 7 THEN 3
             WHEN impacto <= 9 THEN 4 ELSE 5 END,
        1)
    WHEN 'V' THEN 'Muy bajo'
    WHEN 'B' THEN 'Bajo'
    WHEN 'M' THEN 'Medio'
    WHEN 'A' THEN 'Alto'
    ELSE 'Critico'
END;
