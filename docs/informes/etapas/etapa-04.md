# Etapa 04 — Renderizador PDF

Los cinco tipos de informe (MR, TEC, EJE, MIT, FIN) en PDF, con el resolvedor de recursos seguro, la marca NP, el índice y los marcadores. Sin red y sin LLM (R4). El módulo de red no se tocó (R5).

## Hecho
- **Interfaz y registro (§7):** `InformeRenderer` (formato, soporta por tipo, `render(snapshot, opciones, salida)`), `OpcionesRender(noPublicado)` y `RegistroRenderers`, que resuelve por tipo y formato y lanza `IllegalArgumentException` (400 genérico) si la combinación no es válida o no existe.
- **Dependencia (R6, solo §2):** `openhtmltopdf-pdfbox` **1.1.114** (sin Batik, sin `-svg-support`, `-mathml`, `-objects` ni `-java2d`). Las pruebas de la Etapa 0 ya confirmaron la compatibilidad con Spring Boot 4.
- **Motor de plantillas propio (§7.1):** `MotorPlantillasPdf`, un `SpringTemplateEngine` aparte del de la web (no es un bean, para no desplazar al de la web), con `ClassLoaderTemplateResolver` sobre `informes/pdf/plantillas/` en **modo XML**, de modo que la salida es XHTML bien formado.
- **Resolvedor de recursos seguro (obligatorio):** `ResolvedorRecursosSeguro` solo acepta `classpath:informes/...`. Rechaza, registra como advertencia y no carga: `http`, `https`, `file`, `ftp`, `data`, `jar`, `javascript`, letras de unidad, rutas absolutas o protocolo-relativas (`//`), `..`, `\`, `%`, caracteres de control, mayúsculas en el esquema y cualquier ruta fuera de `informes/`. Los protocolos de red y de archivos quedan conectados a una fábrica que no entrega nada. Una segunda barrera (`VerificadorRecursosHtml`) revisa el XHTML ya producido antes de generar el PDF.
- **Generador (`GeneradorPdf`):** fuentes con `useFont` desde el classpath (las de la app, D12: Plus Jakarta Sans normal/negrita/cursiva/negrita cursiva y JetBrains Mono normal/negrita, con su OFL). Un fallo de renderizado se informa con un mensaje genérico.
- **Vista (`ConstructorVistaPdf`):** todo cálculo, orden, color, cifra y texto se decide en Java; las plantillas solo pintan. Los textos fijos (`avisos.md`, `escala-calificacion.md`, `glosario.md`) se versionan con la plantilla.
- **Plantillas (§7):** `base`, `ejecutivo`, `tecnico`, `mitigacion`, `matriz`, `final` y los fragmentos `portada`, `control`, `avisos`, `anexo-a`, `ficha-hallazgo`, `tabla-severidad`, `mapa-calor`, `referencias`, más `narrativa`. **Todo texto de usuario va con `th:text`**; el único `th:utext` del documento está en `fragmentos/narrativa.html` (narrativa ya saneada), documentado y vigilado por una prueba.
- **CSS APA 7 + marca:** Carta, márgenes de 2,54 cm, Plus Jakarta Sans 11 pt con interlineado 1,15, JetBrains Mono para códigos/CVE/IDs; títulos nivel 1 centrado, 2 a la izquierda, 3 negrita cursiva, 4 con sangría y punto final; "Tabla N" en negrita, título en cursiva y "Nota."; numeración consecutiva con contadores CSS (también "Figura N"); encabezado "N de M" y pie `ID · CONFIDENCIAL · SHA-256: <12>` en cada página; paleta de §6.2 con la etiqueta siempre en texto. Barras con clases `w-0…w-100` calculadas en el servidor, **sin SVG y sin estilos en línea**.
- **Contenido por tipo (§6.5):** EJE (resumen, Tabla 1 por severidad, Tabla 2 área × severidad con barras, 5 riesgos principales con frase por dimensión CIA, recuadro del Anexo A, recomendaciones); TEC (índice con puntos guía y número de página, alcance, escala con la matriz de NIST coloreada, resumen, fichas por área, conclusiones, Anexos A y B, referencias APA); MIT (plan por severidad con columna Activo vacía y aviso de fases); MR (resumen, mapa de calor 5×5, registro con la fila entera coloreada por riesgo, aviso de orden, Anexo A); FIN (Parte I, Parte II, conclusiones, paquete de entregables, constancia con líneas de firma y lista de verificación manual).
- **Copias NP:** marca de agua diagonal fija (en cada página), aviso rojo en portada y control, ID con `-NP`, y los faltantes impresos como `[PENDIENTE DE COMPLETAR POR EL AUDITOR]`.
- **Marcadores del PDF:** OpenHTMLtoPDF no usa la propiedad CSS `bookmark-level` sino un elemento `<bookmarks>`; se genera a partir de los títulos de nivel 1 y 2 (`MarcadoresPdf`).
- **Cambio al snapshot (D25):** se agregó `historial` (versiones de la serie) porque §6.3 exige "historial de versiones del tipo" en el Control del documento y el snapshot es la única entrada de los renderizadores.

## Archivos tocados
- Nuevos (main): `informes/render/{InformeRenderer,OpcionesRender,RegistroRenderers,TextosInforme}.java`; `informes/render/pdf/{RenderizadorPdf,GeneradorPdf,ResolvedorRecursosSeguro,VerificadorRecursosHtml,MotorPlantillasPdf,ConstructorVistaPdf,VistaPdf,MarcadoresPdf}.java`; `resources/informes/pdf/{informe.css,informe-horizontal.css}`, `plantillas/{base,ejecutivo,tecnico,mitigacion,matriz,final}.html`, `plantillas/fragmentos/*.html`; `resources/informes/textos/{avisos,escala-calificacion,glosario}.md`.
- Modificados: `pom.xml` (dependencia), `InformeSnapshot`, `ConstructorSnapshot`, `EnsambladorSnapshot` (historial).
- Muestras (datos ficticios): `docs/informes/muestras/` — los cinco tipos publicados y una copia NP del técnico.

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **682 pruebas, 0 fallos, 0 errores** (544 previas + 138 nuevas).
- **Resolvedor (36):** 28 URIs hostiles rechazadas una por una; solo `classpath:informes/...` se acepta; la fábrica de flujos vuelve a validar; el registro neutraliza saltos de línea. **Un servidor local escucha mientras se genera un documento con `http`, `file`, `data`, `../` y `//`: no recibe ninguna conexión.**
- **Plantillas y datos hostiles (34):** exactamente un `th:utext`, en el fragmento de narrativa; ni estilos en línea, ni scripts, ni `th:inline`, ni recursos externos en plantillas y CSS; texto de usuario con `<script>`, `${7*7}`, `[[${1+1}]]`, HTML, comillas, Markdown hostil, caracteres de control, palabras de 3000 letras, emojis y suplentes sueltos: sale escapado, **no se evalúa**, no rompe el documento y no intenta cargar ningún recurso.
- **Renderizador (36):** los cinco tipos, publicados y NP; pie con el hash en **cada** página; marca de agua en cada página NP y ausente en las publicadas; orientación vertical (EJE, TEC, FIN) u horizontal (MR, MIT) en todas las páginas; tildes, ñ y fuentes incrustadas verificadas sobre el texto extraído del PDF; EJE de 3 a 5 páginas, sin CVE ni CVSS; índice con puntos guía y números de página; marcadores; orden de MIT y MR; Anexo A solo si hay aceptados; proyectos sin hallazgos; faltantes NP.
- **Vista (21) y textos fijos (6):** colores y clases de la paleta, anchos de barra ≤ 100, mapa de calor y matriz de NIST, orden del registro, índice condicional, y los avisos de §6.4 palabra por palabra.
- **Rendimiento:** 1000 hallazgos (el máximo): TEC 292 páginas en 8 s, MR 121 páginas en 3 s, MIT 69 páginas en 2 s, ~1 MB cada uno.
- **Revisión visual propia:** rasterizé las páginas con PDFBox y las revisé una por una; eso permitió encontrar y corregir cuatro problemas reales (ver desviaciones).

## Criterios de aceptación
- ✔ Se genera un PDF por tipo con datos de demo ficticios (`docs/informes/muestras/`).
- ✔ Pruebas del resolvedor en verde.
- ✔ Tildes y ñ correctas (texto extraído del PDF y fuentes incrustadas).

## Desviaciones y decisiones pendientes
- **D25 (snapshot):** `historial` agregado al snapshot (ver arriba). Necesita tu visto bueno.
- **D26 (MR y MIT en horizontal desde la primera página):** la librería calcula el ancho del contenido con la primera página, así que alternar páginas con nombre dejaba el contenido angosto. MR (que el plan pide horizontal) y MIT (9 columnas) son horizontales de principio a fin con una hoja de estilos propia; en el FIN el plan de mitigación va en vertical con tipografía pequeña.
- **D27:** en el PDF de la matriz, las columnas Responsable y "Plan de mitigación (cliente)" se rotulan **Resp.** y **Plan cliente** por espacio, con una leyenda; el XLSX conservará los nombres completos.
- **D28 (PDF/A):** opcional en el plan; **no se habilitó** (exige perfil de color y validación adicionales). Queda como mejora.
- **D29:** las tablas del Control del documento no se numeran, para que "Tabla 1" sea el conteo por severidad (como pide §6.5).
- Siguen vigentes D17 (excepción temporal en la prueba de auditoría de la Fase 12), D20/D21 y los defaults de D8/D15.
- **Pendiente tuyo:** revisión visual de las muestras (en especial el logo, los colores y el aspecto general) y verificar las referencias APA.

## Riesgos o deuda detectada
- **Tamaño:** cada PDF pesa ~0,6 MB aunque sea corto; las fuentes se incrustan completas (el subconjunto no se aplica). Hay margen (límite configurable de 25 MiB), pero conviene optimizarlo.
- **Glifos:** una fuente sin un símbolo (por ejemplo ciertos emojis) deja el carácter en blanco; no rompe el documento.
- **Extracción de texto:** en los tramos en cursiva (referencias) PDFBox separa las partes; es de la lectura, no del PDF.
- La marca de agua queda detrás de las tablas con fondo de color (se ve en los márgenes y entre tablas).

ESTADO: EN ESPERA DE "APROBADA ETAPA 04"
