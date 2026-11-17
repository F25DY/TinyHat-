# Etapa 03 — Snapshot, Ensamblador, Markdown y validación

Alcance: §4, §5 y §4.5. No hay llamadas a la red ni a ningún LLM en ninguna parte (R4). El módulo de red no se tocó (R5).
Los disparadores de la base (defensa extra de I3, Etapa 2) quedan **pendientes**, por indicación de la persona.

## Hecho
- **Snapshot (`InformeSnapshot`)**: contrato de datos inmutable, única entrada de los renderizadores. Solo lleva campos que existen en la aplicación (D5): sin descripción ni alcance del proyecto, sin descripción/evidencia/impacto textual del hallazgo, sin justificación del Anexo A.
- **Serialización canónica (`SerializadorSnapshot`)**: JSON con claves ordenadas alfabéticamente (objetos y mapas), sin sangría y sin nulos, con un `JsonMapper` propio; SHA-256 de los bytes UTF-8. Mismo contenido → mismos bytes → mismo hash. Fechas ISO-8601 en America/Bogota.
- **Ensamblador (`EnsambladorSnapshot` + `ConstructorSnapshot`)**: el ensamblador lee proyecto, áreas, vulnerabilidades, nombre de la cuenta, caché local de CVE y, para el FIN, los informes publicados vigentes; el constructor es puro (sin base de datos).
  - **Anexo A**: los `Aceptada` van al Anexo A y no cuentan en las métricas principales (`aceptados` aparte; `falsosPositivos` = 0 porque ese estado no existe).
  - **Severidad** derivada del impacto (5 CRÍTICA … 1 INFORMATIVA) y **riesgo** por la tabla de NIST, con las reglas de `EscalaRiesgo`; **fase sugerida** F1/F2/F3 (§4.4).
  - **Código del hallazgo** `H-` + 6 hex del UUID; **ID del documento** `TH-{año}-{ORG4}-{PRY4}-{TIPO}-v{n}` y sufijo `-NP`.
  - **Métricas** (todas aquí, nunca en plantillas): por severidad, por área × severidad, por estado, mapa de calor 5×5 (`[p-1][i-1]`), `sinProbabilidad`, totales.
  - **CVE solo de la caché local**; CVSS y fecha si están; referencias de parche solo http/https. El ensamblador no depende de ninguna clase que acceda a la red (verificado por reflexión).
  - **Límite**: más de `tinyhat.informes.max-hallazgos` (1000) → `InformeValidacionException`.
  - **Trazabilidad** (solo FIN): versiones PUBLICADAS vigentes de los otros tipos, con ID, formato, SHA-256 y fecha.
- **Órdenes (`OrdenHallazgos`)**: áreas, fichas TEC, MIT, MR y top 5 de EJE con los criterios exactos de §4.3, incluidos los nulos al final.
- **Markdown (`MarkdownSaneado`)**: análisis único con commonmark + tablas GFM; lista blanca de nodos; `HtmlBlock`/`HtmlInline` pasan a texto escapado; `Image` se elimina; `Link` pasa a texto `etiqueta (url)` sin hipervínculo; `#` y `##` se bajan a nivel 3 (máximo 4). Tres salidas del mismo árbol: **HTML** (`escapeHtml(true)`, `sanitizeUrls(true)`, sin ningún atributo), **modelo para Word** (`BloqueNarrativa`) y **texto plano**. Tras sanear, una verificación comprueba que no queda ningún nodo fuera de la lista blanca.
- **Validador (`ValidadorPublicacion`)**: las seis condiciones de §4.5 sobre el snapshot; mensajes de negocio sin detalles internos; con muchos faltantes se detallan 25 y se resume el resto. Constante `[PENDIENTE DE COMPLETAR POR EL AUDITOR]` para las copias NP.
- **Referencias APA (`ReferenciasApa`)**: bloque fijo en `informes/textos/referencias-base.md` + una entrada por CVE citado (formato exacto del plan, `(s.f.)` sin año), sin repetidos y en orden alfabético determinista.
- **Secciones narrativas por tipo** (§6.3) con etiqueta y marca de obligatoria en `TipoInforme`; `Severidad` y `Fase` en el paquete `riesgo`; `InformesProperties` (`plantilla-version`, `max-hallazgos`, `max-narrativa-caracteres`).
- **Dependencias (R6):** `commonmark` y `commonmark-ext-gfm-tables` **0.32.0** (las de §2; verificadas en la Etapa 0).

## Archivos tocados
- Nuevos (main): `informes/{InformesProperties}.java`, `informes/snapshot/{InformeSnapshot,SerializadorSnapshot,ConstructorSnapshot,EnsambladorSnapshot,OrdenHallazgos,GeneradorIdInforme,FormatoFechas,ReferenciasApa}.java`, `informes/markdown/{MarkdownSaneado,NarrativaSaneada,BloqueNarrativa}.java`, `informes/validacion/{ValidadorPublicacion,ResultadoValidacion,InformeValidacionException}.java`, `riesgo/Severidad.java`, `resources/informes/textos/referencias-base.md`.
- Modificados: `TipoInforme.java` (secciones), `HexoraApplication.java` (registra `InformesProperties`), `application.properties`, `pom.xml`.
- Pruebas nuevas: `ConstructorSnapshotTest` 18, `SerializadorSnapshotTest` 7, `OrdenHallazgosTest` 8, `GeneradorIdYFechasTest` 15, `ReferenciasApaTest` 6, `ValidadorPublicacionTest` 21, `SeveridadTest` 14, `MarkdownSaneadoTest` 56, `EnsambladorSnapshotIntegrationTest` 8 (MySQL real) y 4 más en `TipoInformeTest`.

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **544 pruebas, 0 fallos, 0 errores** (388 previas + 156 nuevas).
- **Markdown hostil** — los 7 casos de §11 y 25 más, todos sin etiquetas ni atributos peligrosos: `<script>`, `<img src="file:///etc/passwd">`, `<img src="http://169.254.169.254/…">`, `[x](javascript:alert(1))`, `![x](http://attacker/x.png)`, HTML en bloque, `#` de nivel 1 (→ nivel 3); además iframe, object, embed, svg, style, link, meta, `data:`, `vbscript:`, `file:`, enlaces anidados en imágenes, referencias, bloques de código con info string hostil, tablas/listas/citas con HTML, entidades y etiquetas partidas. La comprobación es doble: **toda etiqueta del HTML resultante está en una lista blanca y ninguna lleva atributos**.
- **Serialización**: mismo snapshot → mismo hash; el orden de inserción de los mapas no cambia el hash; un cambio mínimo sí; claves ordenadas; lectura de vuelta idéntica; vector conocido de SHA-256.
- **Ensamblador (MySQL)**: nada de otro proyecto ni de otra cuenta entra en el snapshot; CVE en caché con CVSS/fecha, CVE sin caché solo con su ID, texto libre ignorado; trazabilidad del FIN (solo TEC publicado, con DOCX y PDF y sus hashes; sin borradores ni despublicados); límite de hallazgos; sin dependencias de red.
- **Anidación extrema** (5000 `>`) se rechaza con un error de validación en lugar de desbordar la pila; un texto de ~20 000 caracteres se procesa en menos de 2 s.

## Criterios de aceptación
- ✔ Todas las pruebas unitarias de §11 de esta área, incluidas las de Markdown con contenido hostil, en verde.
- ✔ Mismo snapshot → mismo hash.

## Desviaciones y decisiones pendientes
- **D20 (APA, para verificar por la persona):** el bloque fijo trae además NIST SP 800-30 Rev. 1 y FIPS 199 (fundamento de la escala de riesgo, D16), y solo la especificación CVSS 3.1 (2019). No incluí CVSS 4.0 porque no tengo el año del documento verificado. En español, el conector de autores es "y" (APA 7 en español) y no "&".
- **D21:** en un enlace, la URL se muestra solo si es `http`, `https` o `mailto`; con cualquier otro esquema (`javascript:`, `data:`, `ftp:`…) queda solo la etiqueta. El plan pide `etiqueta (url)`; esto lo restringe por seguridad.
- **D22:** la condición «proyecto con alcance definido» se evalúa como fechas completas + al menos un área (D5); en TEC y FIN la cubre además la sección obligatoria «Alcance y metodología».
- **D23:** las referencias del snapshot usan asteriscos para la cursiva (`*título*`); cada renderizador los convierte.
- **Pendiente (decisión tuya):** disparadores de la base para I3 (aplazados).
- Siguen vigentes D17 (excepción temporal en la prueba de auditoría de la Fase 12) y los defaults de D8/D15.

## Riesgos o deuda detectada
- El validador exige `Abierta`, `En Progreso` o `Mitigada` en el cuerpo; si se agregan estados nuevos a la aplicación hay que actualizarlo.
- `Map.copyOf` no conserva el orden: los renderizadores deben usar el orden de `Severidad` (no el del mapa) al pintar conteos.
- El saneado de la narrativa ocurre al renderizar; la validación de longitud y de secciones permitidas al **guardar** la narrativa llega con el servicio (Etapa 6).
- Los textos fijos `avisos.md`, `escala-calificacion.md` y `glosario.md` se crean en la Etapa 4 junto con las plantillas.

ESTADO: EN ESPERA DE "APROBADA ETAPA 03"
