# Etapa 05 — Renderizadores DOCX y XLSX

DOCX para TEC, EJE, MIT y FIN; XLSX para MR y MIT. Sin red y sin LLM (R4). El módulo de red no se tocó (R5).

## Hecho
- **Dependencia (R6, solo §2):** `org.apache.poi:poi-ooxml` **5.5.1** (la última 5.x; existe 6.0.0, D9).
- **DOCX (`RenderizadorDocx`, §7.2):** parte de `informes/docx/base.docx` (solo estilos: Título 1–4, Tabla, Nota, Código, Leyenda, Ficha, Aviso, Referencia, Encabezado y Pie) y construye el contenido por código.
  - Mismo contenido que el PDF por tipo: portada con logo, control con historial, avisos, recuadro del Anexo A, tablas con celdas de severidad y riesgo **sombreadas con la paleta y con su etiqueta en texto**, mapa de calor y matriz de NIST como tablas coloreadas, fichas por área, Anexos A y B, referencias APA con cursiva, constancia y lista de verificación del FIN.
  - Encabezado "Página N de M" con los campos simples `PAGE` y `NUMPAGES`; pie con ID, CONFIDENCIAL y hash corto. Carta; MIT horizontal. **Sin índice automático.**
  - La narrativa Markdown ya saneada entra por el modelo `BloqueNarrativa` (párrafos, listas, tablas, código, citas) como texto de runs; nunca como XML.
  - **Prohibido y comprobado:** hipervínculos, relaciones externas, objetos incrustados, macros, campos distintos de PAGE/NUMPAGES. La única relación a medios es el logo, interno al paquete.
- **XLSX (`RenderizadorXlsx`, §7.3):** SXSSF con ventana de 100 filas; `close()` en lugar del `dispose()` obsoleto (D10).
  - MR: hojas **Resumen, Mapa de calor, Registro, Anexo A y Control**. Registro con la **fila completa coloreada por nivel de riesgo**, primera fila fija, autofiltro, anchos fijos, repetición del encabezado al imprimir y las columnas vacías **Activo, Responsable y Plan de mitigación (cliente)**.
  - MIT: hoja **Plan de mitigación** (severidad coloreada, columnas del cliente vacías, primera fila fija y autofiltro) y hoja **Control**.
  - **Barras de datos** con formato condicional en los conteos del Resumen (SXSSF lo permite).
  - **Todo texto de usuario entra con `setCellValue(String)`** (celda STRING); no existe ninguna fórmula en el libro. Se eliminan caracteres no válidos en XML y se recorta a 32 767 caracteres. Un estilo por clase de la paleta (no por fila). El flujo de salida no se cierra (lo cierra quien llama).
- **Utilidad común:** `TextoSeguro` (limpieza para XML y celdas; también la usa el PDF) y `PaletaInforme` (colores de §6.2 compartidos).
- **Registro:** `RegistroRenderers` resuelve ya las **11 combinaciones válidas** de §1.2 y rechaza las demás con `IllegalArgumentException`.

## Archivos tocados
- Nuevos (main): `informes/render/{TextoSeguro,PaletaInforme}.java`, `render/docx/{RenderizadorDocx,ConstructorDocx}.java`, `render/xlsx/RenderizadorXlsx.java`, `resources/informes/docx/base.docx`.
- Modificados: `pom.xml` (POI), `render/pdf/RenderizadorPdf.java` (delega la limpieza de XML en `TextoSeguro`).
- Pruebas nuevas: `render/{OfimaticaPrueba,MuestrasOfimaticaTest}`, `render/docx/{GeneradorBaseDocx,BaseDocxTest,RenderizadorDocxTest}`, `render/xlsx/RenderizadorXlsxTest`.
- Muestras (datos ficticios): `docs/informes/muestras/*.docx` y `*.xlsx` (publicadas y NP).

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **729 pruebas, 0 fallos, 0 errores** (682 previas + 47 nuevas).
- **DOCX (27):** los cuatro tipos, publicados y NP, traen ID, proyecto, auditor, avisos y control; el técnico trae fichas, anexos, referencias y no trae índice; el ejecutivo no trae CVE ni CVSS; orientación (solo MIT horizontal); encabezado con `PAGE`/`NUMPAGES` y pie con ID y hash; NP marca el encabezado y la publicada no; celdas de severidad con los colores de la paleta; estilos de la base; mismo snapshot, mismo texto.
- **Seguridad DOCX:** ZIP sin `vbaProject`, `embeddings`, `activeX`, `oleObject`, `externalLink`, `customXml`, relaciones `TargetMode="External"`, `w:hyperlink`, `w:altChunk`, `w:object` ni campos distintos de PAGE/NUMPAGES; datos hostiles (`=HYPERLINK(...)`, `+cmd`, `-1+1`, `@SUM`, `<script>`, caracteres de control, suplentes sueltos) salen como texto y el documento vuelve a abrirse; Markdown con enlaces `javascript:`, imágenes y HTML queda sin enlaces ni medios nuevos.
- **XLSX (15):** hojas y orden; encabezado fijo, autofiltro `A1:R1` y `A1:K1`, columnas del cliente en blanco; cada fila con el color de su nivel y su etiqueta; orden por puntaje; barras de datos; suma del mapa de calor igual a los hallazgos con probabilidad; anchos fijos.
- **Seguridad XLSX:** con `=HYPERLINK(...)`, `+cmd`, `-1+1`, `@SUM(...)` y otros textos peligrosos como título, área y mitigación, **ninguna celda del libro es de tipo FORMULA ni ERROR** y los peligrosos están como STRING; sin `vbaProject`, relaciones externas, hipervínculos ni elementos `<f>`; texto de 40 000 caracteres recortado; **1000 hallazgos** en menos de 60 s con la misma cantidad de estilos que con pocos.
- **Registro:** las 11 combinaciones válidas se resuelven; las otras 4 lanzan `IllegalArgumentException`.

## Criterios de aceptación
- ✔ Muestras de demo generadas (`docs/informes/muestras/`).
- ✔ Pruebas de inyección de fórmulas y ausencia de relaciones externas y macros en verde.
- ✗ → **tuyo:** "abren sin advertencias en LibreOffice y Office". Aquí no hay ninguno de los dos (D33): se verificó la estructura con POI y con el ZIP, no el aspecto.

## Desviaciones y decisiones pendientes
- **D30:** DOCX nombra las fuentes de la marca pero no las incrusta.
- **D31:** la marca "no publicado" en DOCX es de texto (encabezado, portada, control), no una marca de agua diagonal.
- **D32:** DOCX y XLSX reutilizan la vista del PDF para que las cifras coincidan.
- **D33:** verificación visual en Word, Excel o LibreOffice pendiente de la persona.
- Cierra D10 (`close()` en lugar de `dispose()`).

## Riesgos o deuda detectada
- **Tamaño:** cada DOCX pesa ~0,6 MB porque el logo PNG pesa 686 KB (el PDF tiene la misma deuda). Conviene reducir el logo o aportar una versión optimizada.
- **Fórmulas al editar:** una celda STRING que empieza por `=` se muestra como texto, pero si alguien entra a editarla en Excel y confirma, Excel la evalúa. El plan acepta STRING como defensa; si quieres más, se puede anteponer un apóstrofo (`quotePrefix`) a esas celdas.
- **Nombre `VistaPdf`:** ahora la usan tres formatos (D32).
- El PDF sigue siendo la versión de referencia (§6.4) y el aviso lo dice en las copias DOCX y XLSX.

ESTADO: EN ESPERA DE "APROBADA ETAPA 05" (por indicación de la persona se continúa con la Etapa 6 sin detenerse; se detiene al terminar la 6)
