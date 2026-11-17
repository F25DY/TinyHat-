# Etapa 00 — Inventario, mapeo y prueba de librerías

Rama: `feature/informes` (creada desde `main` en `1aba1e1`, al día con `origin/main`).
No se escribió código de producto ni se tocó el `pom.xml`.

## Hecho

1. Rama creada.
2. Inventario de `Auditoria` (proyecto), `AreaAuditable`, `Vulnerabilidad`, `Empresa` (organización), `Cuenta`, `Usuario`, `Cve` y de todo el módulo de informes que **ya existe**.
3. Tabla de mapeo del snapshot (§1 abajo).
4. Respuestas a las preguntas del punto 4 (§2).
5. Mecanismos reutilizables identificados (§3).
6. Propuesta de rate limit (§4).
7. Prueba de librerías en un proyecto temporal fuera del repo (§5).
8. Verificación de fuentes y logo (§6).
9. Registro de desviaciones en [IMPLEMENTATION_LOG.md](../IMPLEMENTATION_LOG.md) (D1–D11).

## 0. Hallazgos que bloquean o cambian etapas posteriores

El código real contradice premisas del documento en cuatro puntos. Por R3 no los reinterpreté: están en el registro como D1–D5 y necesitan una decisión tuya antes de la Etapa 1.

### D1 — Ya existe un módulo de informes (choca con §3.2 y la Etapa 2)

| Elemento existente | Detalle |
|---|---|
| Migraciones | `V22__create_report_publication.sql`, `V28__report_content_foundation.sql` |
| Tabla | `informe` (un registro por informe; estados `BORRADOR/PUBLICADO`; `contenido_json` con snapshot y `hash_contenido`) |
| Tipos | `TECNICO_PDF`, `EJECUTIVO_PDF`, `PLANES_MITIGACION_PDF`, `MATRIZ_RIESGO_XLSX` |
| Código | `model/Informe`, `repository/InformeRepository`, paquete `report/*` (servicio, controlador REST `/api/proyectos/{id}/informes` y `/api/informes/{id}/…`, `ReportContentAssembler`, `RiskMatrixCalculator`, interfaz `ReportRenderer` **sin implementaciones**) |
| Interfaz | Panel "Informes" en `detalleproyecto.html` + `project-reports.js` + `project-reports.css` |
| Seguridad | `AuditEventType.REPORT_*`, categoría de rate limit `PDF` (ruta `/api/informes/*/descarga`), capacidad `INFORMES_PDF`, `PermissionService.requireInforme` |
| Pruebas | `ReportFoundationIntegrationTest`, `RiskMatrixCalculatorTest`, `FinalEndpointInventoryIntegrationTest` (inventario de endpoints) |

Es una base de la Fase 14 sin renderizadores: la tabla estaba vacía cuando se creó V28. El diseño nuevo (serie → versiones → archivos, 5 tipos, DOCX, publicación con archivos en disco) la reemplaza funcionalmente.

**Opciones**
- **A (recomendada).** Tablas nuevas con nombres distintos (`informe_serie`, `informe_version`, `informe_archivo`) y paquete nuevo `com.hexora.hexora.informes`. El módulo viejo queda intacto y sin tocar hasta la Etapa 7, donde se reemplaza el panel y se retira el código y la tabla antigua en una migración, **solo si confirmas que no hay datos que conservar** (en una BD local puede haber filas de prueba).
- **B.** Migrar y renombrar la tabla vieja de inmediato (`informe` → `informe_legado`) y usar los nombres del documento. Más limpio, pero rompe el panel actual desde la Etapa 2.
- **C.** Evolucionar la tabla vieja. No recomendada: el modelo es incompatible (versiones y archivos múltiples).

### D2 — `probabilidad` ya existe (choca con la Etapa 1)

`vulnerabilidad.probabilidad` es `NOT NULL`, escala **0–10**, ya está en el formulario (`nuvulnerabilidad.html`, `@Min(0) @Max(10)`) y en el cálculo de riesgo. El documento pide agregarla como nullable 1–5.

**Opciones**
- **A (recomendada).** Reutilizar la existente y mapearla a 1–5 con las mismas bandas que ya usa `RiskMatrixCalculator` (0–1→1, 2–3→2, 4–5→3, 6–7→4, 8–10→5). **No hay migración**; la Etapa 1 se reduce a mostrar el nivel de riesgo calculado en el detalle y el listado (o se omite). Consecuencia: `sinProbabilidad` siempre vale 0 y la condición 5 de §4.5 se cumple siempre.
- **B.** Agregar una columna nueva `probabilidad_15` nullable. Duplica el dato y el auditor tendría que registrar la probabilidad dos veces.

### D3 / D4 — Severidad y riesgo

- No hay campo de **severidad**. Lo más cercano es `nivelRiesgo` (Critico/Alto/Medio/Bajo, 4 niveles, sin "Informativa").
- `nivelRiesgo` se calcula en `VulnerabilidadController.calculateRisk`: `impacto = media(C,I,A)`; `puntaje = (impacto + probabilidad) / 2`; umbrales ≥7 Critico, ≥5 Alto, ≥3 Medio, resto Bajo.
- El documento define el riesgo con otra regla (tabla 5×5 sobre probabilidad × impacto 1–5). Para la misma vulnerabilidad los dos niveles pueden diferir, y el panel/dashboard seguiría mostrando el antiguo.

**Propuesta**
- **Severidad** (técnica, sin probabilidad): derivada del impacto 1–5 → 5 = CRITICA, 4 = ALTA, 3 = MEDIA, 2 = BAJA, 1 = INFORMATIVA.
- **Riesgo**: la tabla de §4.2, tal como está en el documento.
- Dejar `nivelRiesgo` actual sin tocar, y documentar en `escala-calificacion.md` que el informe usa la escala del documento. Alternativa: alinear `nivelRiesgo` a la tabla nueva (cambia comportamiento del dashboard; lo desaconsejo dentro de este componente).

### D5 — Campos del contrato que no existen

Según §4, se omiten del snapshot y la plantilla no los pinta. Consecuencias que conviene que apruebes:

| Falta | Efecto |
|---|---|
| `proyecto.descripcion`, `proyecto.alcance` | La condición 1 de §4.5 ("el proyecto tiene alcance definido") no se puede evaluar. Propuesta: cubrirla con la sección narrativa obligatoria `alcanceMetodologia` (TEC, FIN) y, para los demás tipos, exigir fechas + al menos un área. |
| `hallazgo.descripcion`, `evidencia`, `impacto` (texto) | Las fichas del TEC quedan solo con identificación, calificación, CVE (su descripción viene del caché de CVE) y mitigación. |
| `anexoA.justificacion` | El Anexo A lista código, título, área y tipo, sin justificación. |
| Estado **falso positivo** | No existe: los estados permitidos son `Abierta / En Progreso / Mitigada / Aceptada`. `falsosPositivos` valdrá siempre 0. |
| `auditor.nombre` (D6) | `Usuario` solo tiene correo. Propuesta: usar `Cuenta.nombre` (nombre de la cuenta, 150 caracteres). Evito el correo para no exponer datos personales en un documento de cliente. |

Si prefieres campos nuevos (descripción, evidencia, justificación), eso contradice "no se crean campos nuevos" y requiere tu aprobación explícita y probablemente una etapa adicional.

## 1. Tabla de mapeo (snapshot §4 → entidad real)

| Campo del snapshot | Origen real | Estado |
|---|---|---|
| `informe.tipo / version / id / estado` | Se crean con el modelo nuevo | Nuevo |
| `organizacion.nombre` | `Empresa.nombre` (`organizacion_cliente.nombre`) | ✔ |
| `proyecto.nombre` | `Auditoria.nombre` | ✔ |
| `proyecto.descripcion` | — | **NO EXISTE** |
| `proyecto.alcance` | — (solo existen rangos de red, que son del módulo de red, R5) | **NO EXISTE** |
| `proyecto.fechaInicio` | `Auditoria.fechaInico` (el atributo está escrito así) | ✔ |
| `proyecto.fechaFin` | `Auditoria.fechaFin` | ✔ |
| `auditor.nombre` | `Usuario` no tiene; `Cuenta.nombre` | **NO EXISTE** (ver D6) |
| `areas[].nombre / descripcion` | `AreaAuditable.nombre / descripcion` | ✔ |
| `hallazgo.codigo` | No hay código legible → `H-` + 6 hex de `Vulnerabilidad.publicId` (regla de §4.1) | Derivado |
| `hallazgo.titulo` | `Vulnerabilidad.nombre` | ✔ |
| `hallazgo.descripcion / evidencia / impacto` | — | **NO EXISTE** |
| `hallazgo.mitigacion` | `Vulnerabilidad.planMitigacion` (nullable) | ✔ |
| `hallazgo.estado` | `Vulnerabilidad.estado` | ✔ |
| `hallazgo.severidad` | No existe; derivada (D3) | Derivado |
| `hallazgo.cia.c/i/a` | `confidencialidad / integridad / disponibilidad` (0–10) | ✔ |
| `hallazgo.probabilidad` | `Vulnerabilidad.probabilidad` (0–10, NOT NULL) → banda 1–5 (D2) | ✔ con mapeo |
| `hallazgo.impacto15` | Derivado de CIA (propuesta §2) | Derivado |
| `hallazgo.riesgo.puntaje / nivel` | Derivado con la tabla §4.2 (≠ `nivelRiesgo`, D4) | Derivado |
| `cves[].id` | `Vulnerabilidad.cve` (**un solo** CVE por hallazgo, texto libre de hasta 30) | ✔ (lista de 0 o 1) |
| `cves[].cvss` | `Cve.puntajeCvss` (también `versionCvss`) | ✔ si el CVE está en caché |
| `cves[].publicado` | `Cve.fechaPublicacion` (nullable) | ✔ si está en caché |
| `cves[].url` | Se construye con el id (`https://nvd.nist.gov/vuln/detail/{id}`), sin red | Derivado |
| `referencias` | `Cve.referenciasParche` (solo referencias de parche) | Parcial |
| `faseSugerida` | Derivado de la severidad (§4.4) | Derivado |
| `anexoA` | Estado `Aceptada` → ACEPTADO. `FALSO_POSITIVO` y justificación no existen | Parcial |
| `metricas.*` | Calculadas en el Ensamblador | Nuevo |
| `trazabilidad` | Del modelo nuevo (solo FIN) | Nuevo |
| `narrativa` | Del modelo nuevo | Nuevo |
| `referenciasApa` | Textos fijos + CVE citados | Nuevo |

Datos existentes que **no** están en el contrato y por tanto no se usarán salvo que lo apruebes: `tipo`, `herramienta`, `host`, `ipAfectada`, `puerto`, `servicio`, `fechaDeteccion`, `Auditoria.tipoAuditoria`, `Empresa.nit/sector`.

## 2. Respuestas a las preguntas del punto 4

1. **Escala de la CIA y severidad.** Cada dimensión es un entero **0–10** (validado en `VulnerabilidadForm`). No hay severidad propia. El impacto existente es la **media** de C, I, A (2 decimales).
   **Propuesta CIA → impacto 1–5:** `impacto15 = banda(max(C, I, A))` con las bandas 0–1→1, 2–3→2, 4–5→3, 6–7→4, 8–10→5. Usa `max` (como propone el documento) y las mismas bandas que `RiskMatrixCalculator`. Alternativa: usar la media, que coincide con el impacto actual de la app pero atenúa un hallazgo grave en una sola dimensión.
2. **Estados.** Aceptado → `Aceptada`. Falso positivo → **no existe** (la validación solo admite `Abierta|En Progreso|Mitigada|Aceptada`).
3. **Código legible.** **No** existe. Se usa el respaldo `H-` + 6 caracteres hexadecimales del `publicId`.
4. **CVE en caché.** Sí guarda `fecha_publicacion` y `puntaje_cvss` (más `version_cvss`, `severidad`, `cwe`, `referencias_parche`), pero solo si el CVE fue consultado antes; `fecha_publicacion` es nullable. `Vulnerabilidad.cve` es texto libre sin clave foránea.

## 3. Mecanismos que se reutilizarán

| Tema | Mecanismo existente |
|---|---|
| Aislamiento | Filtro Hibernate `accountFilter` (`cuenta_id = :tenantId`) + `TenantContext`/`TenantScopedEntityListener`; repositorios con `…AndCuentaId`; `PermissionService.requireProyecto/requireInforme` → `ScopedNotFoundException` (404). El tenant es la **cuenta**; la organización cuelga de ella y los CLIENTE_ADMIN se limitan por `organizacion_id`. Consultas `findVisible…` ya resuelven el rol (AUDITOR por asignación, CLIENTE_ADMIN solo PUBLICADO de su organización). |
| Auditoría (Fase 12) | `AuditService.reportEvent(AuditEventType, principal, request, publicId, Map detalle)` y enum `AuditEventType` (nombres en inglés `REPORT_*`). `audit_event.evento` es `VARCHAR(80)`: agregar valores `INFORME_*` **no requiere migración**. La tabla es append-only. |
| Rate limit | Propiedades `tinyhat.security.ratelimit.<categoría>.capacity/refill-tokens/refill-seconds`; `RateLimitCategory` (enum de paquete) clasifica por ruta; filtros `IpRateLimitFilter` y `UserRateLimitFilter` (clave `user:{id}:{categoría}`). Existe la categoría `PDF` (5 por 60 s). |
| Notificación | Interfaz `Notifier` (4 métodos fijos), `SmtpNotifier` (**solo perfil `prod`**, `send` privado), `ConsoleNotifier`. Habrá que extender la interfaz (D8). |
| Errores | `CorrelationIdFilter` (MDC), `SecurityErrorResponse(code, correlationId)`, `ApiExceptionHandler` (400 `VALIDATION_ERROR` para `IllegalArgumentException`, tal como pide §7), `ScopedNotFoundExceptionHandler`, `error.html`. |
| Rutas | MVC en español con UUID públicos: `/proyectos/{publicId}`, `/proyectos/{p}/departamentos/{a}/vulnerabilidades/...`. Las rutas de §9 (`/proyectos/{uuid}/informes`, `/informes/versiones/...`, `/cliente/informes`) no chocan con las REST actuales, que viven bajo `/api/`. |
| Migraciones | Último: **V29**. La siguiente es **V30**. Flyway sin `out-of-order`; `RuntimePrivilegeMigrationStrategy` concede permisos tabla por tabla tras migrar (las tablas nuevas lo heredan). |
| Capacidades | `Capability.INFORMES_PDF` (por cuenta, por defecto activa) ya condiciona los informes actuales. **Pregunta:** ¿los informes nuevos deben respetarla? Propongo que sí (mismo interruptor). |
| CSP / estáticos | `ContentSecurityPolicy` sin inline; CSS/JS en `static/css` y `static/js`. La vista previa de §9 irá en `/css/informe-vista-previa.css`. |
| Pruebas | Testcontainers MySQL vía `IsolatedMySqlTestDatabase`; hay `FrontendTemplatesComplianceTest` que vigilará las plantillas web nuevas. |

## 4. Propuesta de rate limit `INFORME_GENERACION` (D7)

Hoy el límite autenticado es **por usuario**, no por cuenta; propongo seguir esa convención (un auditor es un usuario) en vez de crear una dimensión nueva.

```properties
tinyhat.security.ratelimit.informe-generacion.capacity=5
tinyhat.security.ratelimit.informe-generacion.refill-tokens=5
tinyhat.security.ratelimit.informe-generacion.refill-seconds=60
```

Mismo valor que la categoría `PDF` (5 por minuto) y coherente con el semáforo de concurrencia máxima 2. Cubre `POST …/vista-previa`, `…/generar` y `…/publicar`. Las descargas de informes publicados usarían la categoría `PDF` existente.

## 5. Prueba de librerías

Proyecto temporal en el scratchpad de la sesión (**no está en el repositorio**), JDK 27.0.1, con un script que genera los tres formatos.

| Librería | Versión elegida | Resultado |
|---|---|---|
| `io.github.openhtmltopdf:openhtmltopdf-pdfbox` | **1.1.114** (trae `-core` 1.1.114, PDFBox 3.0.8) | ✔ PDF generado con TTF desde el classpath (`useFont`) y con `@page`/`counter(page)`. No arrastra Batik ni módulos SVG. |
| `org.apache.poi:poi-ooxml` | **5.5.1** (última 5.x; existe 6.0.0) | ✔ DOCX y XLSX (SXSSF) generados. Una celda `=HYPERLINK(...)` cargada con `setCellValue(String)` queda de tipo **STRING**. Autofiltro y primera fila fija funcionan. |
| `org.commonmark:commonmark` y `commonmark-ext-gfm-tables` | **0.32.0** | ✔ Tablas GFM; `escapeHtml(true)` convierte `<script>` en texto escapado; `[x](javascript:…)` queda con `href=""`. |

**Resolvedor de recursos.** Con un `FSUriResolver` propio que devuelve `null`, las peticiones a `http://127.0.0.1:9/…` y `file:///C:/Windows/win.ini` fueron **rechazadas y registradas** sin cargarse. Confirma que el diseño de §7.1 es viable.

**Compatibilidad con Spring Boot 4.0.6.** Copia del `pom.xml` con las cuatro dependencias: el árbol resuelve sin conflictos (commons-codec 1.19.0, commons-lang3 3.19.0, commons-io 2.21.0, commons-logging 1.3.6 y log4j 2.25.4 gestionados o compatibles). No se ejecutó la aplicación completa con ellas.

**Observaciones**
- La **1.1.114 se publicó hoy** (`lastUpdated` 2026-11-16 en Maven Central). El documento pide la última estable; te propongo mantenerla, pero si prefieres una versión más asentada (por ejemplo 1.1.113) lo ajusto.
- `commonmark` `sanitizeUrls` deja un `<a rel="nofollow" href="">` vacío: no sirve para el requisito "Link como `etiqueta (url)`"; en la Etapa 3 se resuelve recorriendo el árbol (AST), como indica §5.
- Las tildes y la ñ se probaron con Arial como fuente provisional; se re-verificarán con Source Sans 3 cuando estén los TTF.
- Las fuentes web que ya trae la app (`static/vendor/fonts`) son **WOFF2**, que PDFBox no puede usar.

## 6. Fuentes y logo (pendiente de ti)

| Recurso | Estado | Ruta destino |
|---|---|---|
| Source Sans 3 TTF estáticos: Regular, Bold, Italic, BoldItalic | **Faltan** | `hexora/src/main/resources/informes/fuentes/` |
| JetBrains Mono TTF estáticos: Regular, Bold | **Faltan** (solo hay WOFF2) | ídem |
| `OFL.txt` de ambas familias | **Falta** | ídem |
| Logo PNG | Existe `static/imagen/logo.png` (activo web). No existe `informes/pdf/img/logo-tinyhat.png` | `hexora/src/main/resources/informes/pdf/img/logo-tinyhat.png` |

Tengo acceso a la red y podría descargar las fuentes de sus repositorios oficiales (licencia SIL OFL). Por R6/§2 prefiero que lo confirmes antes. Sobre el logo: ¿copio `logo.png` actual como `logo-tinyhat.png` o aportarás uno específico para informes?

## Archivos tocados

- `docs/informes/IMPLEMENTATION_LOG.md` (nuevo)
- `docs/informes/etapas/etapa-00.md` (nuevo)
- Código de producto y `pom.xml`: **sin cambios**.

## Pruebas (comando + resultado)

| Qué | Resultado |
|---|---|
| `java -cp "lib/*;res" LibTest.java` (proyecto temporal) | PDF 17 239 bytes, DOCX 2 291 bytes, XLSX 3 516 bytes; 6 recursos externos rechazados; celda de fórmula = STRING |
| `mvnw dependency:tree` sobre una copia del pom con las dependencias | Resolución correcta, sin conflictos |
| Suite del proyecto | **No se ejecutó**: esta etapa no cambia código. La línea base se medirá al inicio de la Etapa 1. |

## Criterios de aceptación

- ✔ Tabla de mapeo completa (§1).
- ✔ Las cuatro preguntas del punto 4 respondidas (§2). Dos de ellas con respuesta "no existe" que requieren decisión (D3, D5).
- ✔ Las tres librerías funcionan en el entorno (JDK 27; el `pom` apunta a 25).
- ✗ Fuentes y logo: faltan (§6).

## Desviaciones y decisiones pendientes

Necesito tu respuesta a lo siguiente antes de aprobar y pasar a la Etapa 1:

1. **D1** — Modelo viejo de informes: ¿opción A (convivir con nombres nuevos y retirar el viejo en la Etapa 7), B o C?
2. **D2** — Probabilidad: ¿A (reutilizar la existente con bandas 1–5; Etapa 1 casi vacía) o B (columna nueva)?
3. **D3/D4** — Severidad derivada del impacto y riesgo con la tabla de §4.2, sin tocar `nivelRiesgo` actual: ¿aprobado? ¿CIA → impacto con `max` y bandas de 2 en 2?
4. **D5/D6** — Campos inexistentes: ¿se omiten tal como indica el documento, con la condición de alcance reemplazada como propuse? ¿`auditor.nombre` = `Cuenta.nombre`?
5. **D7/D8** — Rate limit por usuario (5/60 s) y ampliación de la interfaz `Notifier` en la Etapa 6.
6. ¿Los informes nuevos respetan la capacidad `INFORMES_PDF`?
7. Fuentes TTF y logo (§6).
8. Versión de openhtmltopdf (1.1.114, publicada hoy).

## Riesgos o deuda detectada

- Convivencia de dos sistemas de informes si se elige D1-A: hay que cuidar que `FinalEndpointInventoryIntegrationTest` y la UI no se contradigan hasta la Etapa 7.
- Dos definiciones de "nivel de riesgo" (la del dashboard y la del informe). Se documentará en `escala-calificacion.md`.
- La BD local de desarrollo (MySQL en el puerto 3307) puede tener filas en `informe`; no se inspeccionó.
- Sin cifrado en reposo (aceptado en §1.6) hasta usar datos reales.

ESTADO: EN ESPERA DE "APROBADA ETAPA 00"
