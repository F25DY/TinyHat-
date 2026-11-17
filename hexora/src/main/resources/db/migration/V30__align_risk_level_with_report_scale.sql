-- Un solo nivel de riesgo (informes y aplicación).
-- Antes: impacto = media de C/I/D y nivel = ((impacto + probabilidad) / 2) con umbrales 3/5/7.
-- Ahora: impacto = dimensión C/I/D más alta; probabilidad e impacto pasan a la escala 1-5 por bandas
-- (0-1, 2-3, 4-5, 6-7, 8-10) y el nivel sale de una tabla de búsqueda (P x I).
-- Esta regla es la de com.hexora.hexora.riesgo.EscalaRiesgo y la del formulario (vulnerability-form.js);
-- EscalaRiesgoMigracionIntegrationTest comprueba que el SQL y Java coinciden en todas las combinaciones.

UPDATE vulnerabilidad
SET impacto = GREATEST(0, LEAST(10, GREATEST(confidencialidad, integridad, disponibilidad)));

-- Cada fila de ELT es una probabilidad (1..5); cada carácter, un impacto (1..5):
-- B = Bajo, M = Medio, A = Alto, C = Critico.
UPDATE vulnerabilidad
SET nivel_riesgo = CASE SUBSTRING(
        ELT(LEAST(5, FLOOR(GREATEST(0, LEAST(10, probabilidad)) / 2) + 1),
            'BBBMM', 'BBMMA', 'BMMAC', 'BMACC', 'MACCC'),
        LEAST(5, FLOOR(GREATEST(0, LEAST(10, impacto)) / 2) + 1), 1)
    WHEN 'B' THEN 'Bajo'
    WHEN 'M' THEN 'Medio'
    WHEN 'A' THEN 'Alto'
    ELSE 'Critico'
END;
