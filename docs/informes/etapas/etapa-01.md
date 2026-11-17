# Etapa 01 — Probabilidad en la vulnerabilidad

Alcance ajustado por la decisión D2 (aprobada): se **reutiliza** `vulnerabilidad.probabilidad` (0–10) y se presenta en la escala 1–5 de los informes. No hay migración ni cambio de esquema.

## Hecho
- **Un solo nivel de riesgo.** La app ya no tiene dos: `nivelRiesgo` se calcula con la regla de informes (tabla probabilidad × impacto de §4.2), al guardar, en el formulario y para los datos existentes. Lo que muestra el dashboard, el listado, el detalle y los informes es el mismo valor.
- **Dependencia corregida.** La regla vive en el paquete neutral `com.hexora.hexora.riesgo` (`EscalaRiesgo`). `Vulnerabilidad`, `VulnerabilidadController` y, más adelante, los informes dependen de él; `model` ya no depende de `informes`.
- **Regla:** probabilidad 0–10 e impacto → banda 1–5 (0-1, 2-3, 4-5, 6-7, 8-10). Impacto = dimensión C/I/D más alta (antes: la media). Nivel = tabla de búsqueda, no umbrales. El impacto guardado pasa a ser ese máximo (0–10), para que el número mostrado explique el nivel.
- **Servidor:** `VulnerabilidadController.calculateRisk` usa `EscalaRiesgo`; lo que manda el cliente se sigue ignorando.
- **Formulario (JS):** `vulnerability-form.js` calcula la misma regla (impacto, puntaje P×I sobre 25, nivel y color).
- **Datos existentes:** migración `V30__align_risk_level_with_report_scale.sql` recalcula `impacto` y `nivel_riesgo` de todas las vulnerabilidades.
- **Vistas:** detalle muestra un solo nivel (se quitó la línea "En informes"); bajo Probabilidad se lee "Posible (3 de 5)"; el listado del área tiene la columna Probabilidad; el formulario trae la ayuda de §10.
- **Sin cambios:** permisos, validaciones, estados y flujos.

## Archivos tocados
- `riesgo/EscalaRiesgo.java` (movido desde `informes/`, ampliado) y sus pruebas
- `riesgo/EscalaRiesgoMigracionIntegrationTest.java` (nuevo)
- `model/Vulnerabilidad.java`, `controller/VulnerabilidadController.java`
- `db/migration/V30__align_risk_level_with_report_scale.sql` (nuevo)
- `static/js/vulnerability-form.js`
- `templates/detallevulnerabilidad.html`, `detalle-area.html`, `nuvulnerabilidad.html`

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **343 pruebas, 0 fallos, 0 errores** (297 previas + 46 nuevas).
- `EscalaRiesgoTest` (45): bandas, impacto, etiquetas, los **25 casos de la tabla**, cálculo de vulnerabilidad, y que la tabla de `vulnerability-form.js` es idéntica a la de Java.
- `EscalaRiesgoMigracionIntegrationTest` (1): ejecuta el SQL real de V30 en MySQL (Testcontainers) sobre **363 combinaciones** de C/I/D y probabilidad y lo compara con Java; todo dentro de una transacción revertida.
- Una primera corrida de línea base dio 4 errores por una condición de carrera mía con `target/`; no eran fallos reales. No se midió la suite sobre `main` limpio.

## Criterios de aceptación
- ✔ La migración aplica sobre datos existentes sin perderlos (solo recalcula dos columnas; el nivel antiguo se sobrescribe a propósito).
- ✔ El nivel es el mismo en la app y en los informes (una sola regla, comprobada SQL = Java = JS).
- ✔ Pruebas nuevas y existentes en verde.
- ➖ Selector 1–5 de §10: no se implementó; el campo conserva su escala 0–10 con ayuda de correspondencia.

## Desviaciones y decisiones pendientes
- **D4 revisada (por indicación de la persona):** el nivel de riesgo de la app cambia para alinearse con el informe. Efecto: algunas vulnerabilidades existentes cambian de nivel (p. ej. una con una sola dimensión C/I/D alta ahora sube; antes la media la diluía). Dashboard y conteos mostrarán los niveles nuevos.
- `impacto` guardado = máximo C/I/D (antes media). Aprobado implícitamente con "derivar" (D3); se avisa por si algún informe o vista externa asumía la media.
- Siguen sin confirmar los defaults D8/D15 (`INFORMES_PDF`, ampliar `Notifier`, openhtmltopdf 1.1.114).

## Riesgos o deuda detectada
- El módulo viejo `report` (`RiskMatrixCalculator`, que usa bandas 0–10 propias) sigue con su lógica hasta que lo sobrescribamos en la Etapa 2.
- Revisión visual pendiente: formulario (tarjeta de cálculo), detalle y listado del área.

ESTADO: **APROBADA ETAPA 01** (indicado por la persona al pedir este ajuste)
