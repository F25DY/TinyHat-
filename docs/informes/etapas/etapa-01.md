# Etapa 01 — Probabilidad en la vulnerabilidad

Alcance ajustado por la decisión D2 (aprobada): se **reutiliza** `vulnerabilidad.probabilidad` (0–10) y se presenta en la escala 1–5 de los informes. No hay migración ni cambio de esquema.

## Hecho
- **Un solo nivel de riesgo, alineado con NIST.** La app y los informes usan la misma regla, con 5 niveles: Muy bajo, Bajo, Medio, Alto y Crítico (el "Very High" de NIST).
  - **Impacto:** la dimensión C/I/D más alta (regla del punto más alto de FIPS 199).
  - **Cortes 0–10 → 5 niveles (NIST SP 800-30 Rev. 1, Tablas G/H, escalados):** 0 Muy bajo · 1–2 Bajo · 3–7 Medio · 8–9 Alto · 10 Muy alto.
  - **Riesgo:** Tabla I-2 de NIST SP 800-30 Rev. 1 (p. I-1 del PDF oficial), copiada del documento y verificada celda por celda en pruebas.
  - **Probabilidad (etiquetas NIST):** Muy baja, Baja, Media, Alta, Muy alta.
- **Dependencia corregida.** La regla vive en `com.hexora.hexora.riesgo.EscalaRiesgo`; `model`, controladores, dashboard e informes dependen de ella, no al revés.
- **Servidor, formulario y datos existentes.** `VulnerabilidadController` calcula con `EscalaRiesgo`; `vulnerability-form.js` replica tabla y cortes; la migración `V31` recalcula `impacto` y `nivel_riesgo` de lo ya guardado. (`V30` queda en el historial de la rama porque una migración ya registrada no se edita; `V31` la sobrescribe de forma autosuficiente.)
- **Cinco niveles en toda la interfaz.** Dashboard, barra de exposición del proyecto y del área, listado, detalle e insignias (Muy bajo = azul) salen de la enumeración, sin niveles escritos a mano. Detalle y listado muestran un solo nivel.
- **Sin cambios:** permisos, validaciones, estados y flujos.

## Archivos tocados
- `riesgo/EscalaRiesgo.java` (nuevo paquete) y pruebas `EscalaRiesgoTest`, `EscalaRiesgoMigracionIntegrationTest`
- `model/Vulnerabilidad.java`, `controller/{Vulnerabilidad,Area,Proyectos}Controller.java`, `service/DashboardService.java`
- `db/migration/V30__…sql`, `V31__risk_level_nist_five_levels.sql`
- `static/js/vulnerability-form.js`, `static/css/style.css`
- `templates/detallevulnerabilidad.html`, `detalle-area.html`, `nuvulnerabilidad.html`
- `DashboardIntegrationTest` (ahora espera 5 niveles)

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **344 pruebas, 0 fallos, 0 errores** (297 previas + 47 nuevas).
- `EscalaRiesgoTest` (46): cortes NIST, impacto, etiquetas, **los 25 casos de la Tabla I-2**, enumeración, y que la tabla y los cortes de `vulnerability-form.js` son los de Java.
- `EscalaRiesgoMigracionIntegrationTest` (1): ejecuta el SQL real de V31 en MySQL sobre **363 combinaciones** y lo compara con Java (transacción revertida).
- Fuente de la matriz: [NIST SP 800-30 Rev. 1](https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-30r1.pdf), Tabla I-2 (p. I-1) y Tablas G/H.

## Criterios de aceptación
- ✔ Migración sobre datos existentes sin pérdida (solo recalcula `impacto` y `nivel_riesgo`).
- ✔ Mismo nivel en app e informes (una regla; SQL = Java = JS comprobado).
- ✔ Pruebas nuevas y existentes en verde.
- ➖ Selector 1–5 de §10: no se implementó; el campo conserva 0–10 con ayuda de correspondencia.

## Desviaciones y decisiones pendientes
- **D4 revisada y D16 (aprobadas por la persona):** niveles de riesgo pasan de 4 a 5 y siguen NIST; etiquetas de probabilidad de NIST en lugar de Rara…Casi segura (§10); tabla de §4.2 sustituida por la Tabla I-2.
- **Efecto en datos:** muchos niveles cambian (arriba y abajo). Un 5/5/5 con probabilidad 5 pasa de Alto a **Medio**; una sola dimensión muy alta sube.
- **Pendiente para Etapa 4:** la paleta de §6.2 define cinco colores para severidad (Crítica…Informativa); para riesgo se usará Crítico, Alto, Medio, Bajo y Muy bajo (azul).
- **Pendiente para Etapa 2:** el módulo viejo `report` (`RiskMatrixCalculator`) sigue con 4 niveles hasta sobrescribirlo.
- Siguen sin confirmar los defaults D8/D15 (`INFORMES_PDF`, ampliar `Notifier`, openhtmltopdf 1.1.114).

## Riesgos o deuda detectada
- Los cortes de NIST concentran los valores 3–7 en "Medio"; es lo que dice el estándar, pero conviene revisarlo con datos reales.
- Revisión visual pendiente: formulario (tarjeta de cálculo), detalle, listado y dashboard con la insignia azul "Muy bajo".

ESTADO: **APROBADA ETAPA 01** (indicado por la persona al pedir el ajuste)
