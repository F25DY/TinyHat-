# Etapa 01 — Probabilidad en la vulnerabilidad

Alcance ajustado por la decisión D2 (aprobada): se **reutiliza** `vulnerabilidad.probabilidad` (0–10) y se presenta en la escala 1–5 de los informes. No hay migración ni cambio de esquema.

## Hecho
- Clase pura `EscalaRiesgo`: banda 0–10 → 1–5 (0-1, 2-3, 4-5, 6-7, 8-10), impacto = banda del máximo de C/I/A, etiquetas (Rara, Improbable, Posible, Probable, Casi segura), puntaje P×I y nivel por **tabla de búsqueda** de §4.2.
- `Vulnerabilidad`: accesores derivados (`probabilidadEscala`, `etiquetaProbabilidad`, `impactoEscala`, `riesgoInforme`). No se persisten.
- Detalle de vulnerabilidad: bajo Probabilidad se muestra "En informes: N · Etiqueta"; bajo Nivel de riesgo, "En informes: Nivel (probabilidad × impacto)".
- Listado del área: columna nueva "Probabilidad" (N · Etiqueta).
- Formulario: texto de ayuda con la correspondencia 0–10 → 1–5 y la frase de §10.
- `nivelRiesgo` actual, validaciones, permisos y flujos: **sin cambios** (D4).

## Archivos tocados
- `hexora/src/main/java/com/hexora/hexora/informes/EscalaRiesgo.java` (nuevo)
- `hexora/src/test/java/com/hexora/hexora/informes/EscalaRiesgoTest.java` (nuevo)
- `hexora/src/main/java/com/hexora/hexora/model/Vulnerabilidad.java`
- `hexora/src/main/resources/templates/detallevulnerabilidad.html`, `detalle-area.html`, `nuvulnerabilidad.html`

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **339 pruebas, 0 fallos, 0 errores** (297 previas + 42 nuevas).
- `EscalaRiesgoTest`: 42 casos (bandas, impacto, etiquetas, **los 25 casos de la tabla P×I**, rechazo fuera de escala).
- Nota: una primera ejecución de línea base dio 4 errores porque mis cambios de plantillas llegaron a `target/` a mitad de la corrida; no son fallos reales. La suite no se midió sobre `main` limpio.
- Ambas corridas imprimen "Surefire is going to kill self fork JVM" tras `System.exit(0)`; el resultado es exit 0.

## Criterios de aceptación
- ✔ Migración sobre datos existentes: no aplica (sin migración, sin pérdida de datos).
- ✔ Campo opcional/validado: se mantiene la validación existente (0–10, obligatorio); D2 elimina la necesidad de un campo nuevo.
- ✔ Pruebas nuevas y existentes en verde.
- ➖ El selector 1–5 de §10 no se implementó a propósito: el valor 0–10 alimenta el `nivelRiesgo` actual y cambiarlo alteraría el dashboard.

## Desviaciones y decisiones pendientes
- La tabla de §4.2 y las bandas viven ya en `EscalaRiesgo` (se adelantó parte de la Etapa 3). Sus pruebas cubren los 25 casos.
- Sigue pendiente la confirmación de los defaults D8/D15 (`INFORMES_PDF`, ampliar `Notifier`, openhtmltopdf 1.1.114).
- Revisión visual de la persona: detalle de vulnerabilidad y listado de área.

## Riesgos o deuda detectada
- La pantalla muestra dos niveles de riesgo (el actual y el "En informes"); está etiquetado, pero puede confundir a usuarios.
- `Vulnerabilidad` ahora depende del paquete `informes` (acoplamiento pequeño y de una sola dirección).

ESTADO: EN ESPERA DE "APROBADA ETAPA 01"
