# Frontend: bibliotecas alojadas localmente (`/vendor`) y cumplimiento de la CSP

Este documento explica por qué la interfaz no carga nada de internet, qué archivos de terceros contiene el repositorio, de dónde salen y cómo comprobarlos o actualizarlos.

## Por qué

La política de seguridad de contenido (Fase 9) es `default-src 'self'; script-src 'self'; style-src 'self'; font-src 'self'`. El navegador **no ejecuta ni carga** lo que no cumpla: un `<script>` en línea, un atributo `style="..."`, un `onclick="..."` o una hoja de un CDN. El diseño nuevo (rama `Correcion-frontend`) usaba los tres: Bootstrap, Font Awesome y Google Fonts por CDN, seis scripts en línea y manejadores `onclick`. Con la CSP aplicada, las pantallas habrían quedado sin estilos y sin comportamiento.

En vez de relajar la CSP, los recursos se alojan en `hexora/src/main/resources/static/vendor/`, los scripts viven en `static/js/` y los estilos en línea pasaron a clases CSS. Efectos secundarios buenos: ninguna petición a terceros (el navegador no revela qué usuarios usan la aplicación) y ningún recurso externo que pueda cambiar o desaparecer.

## Contenido de `/vendor` (27 archivos, 864 KB)

| Biblioteca | Versión | Origen | Licencia |
|---|---|---|---|
| Bootstrap (solo CSS) | 5.3.3 | `cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css` | MIT |
| Font Awesome Free (CSS y tipografías woff2) | 6.4.0 | `cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0` | Iconos CC BY 4.0, tipografías SIL OFL 1.1, código MIT |
| Plus Jakarta Sans y JetBrains Mono (woff2, subconjuntos `latin` y `latin-ext`) | `@fontsource/*` 5.3.0 | `cdn.jsdelivr.net/npm/@fontsource/...@5.3.0/files` | SIL OFL 1.1 |

Los avisos de derechos de autor y licencia van dentro de las propias hojas (`bootstrap.min.css` y `all.min.css` conservan su cabecera; `fonts.css` lo indica).

### Integridad

Las huellas publicadas oficialmente **coinciden** con las de los archivos descargados (se comprobó al incorporarlos):

- Bootstrap 5.3.3 CSS: `sha384-QWTKZyjpPEjISv5WaRU9OFeRpok6YctnYmDr5pNlyT2bRjXh0JMhjY6hW+ALEwIH`
- Font Awesome 6.4.0 `all.min.css`: `sha512-iecdLmaskl7CVkqkXNQ/ZH/XLlvWZOJyj7Yy7tcenmpD1ypASozpmT/E0iPtmFIB46ZmdtAc9eNBvH0H/ZpiBw==`

Cambios respecto al original: a `bootstrap.min.css` se le quitó el comentario `sourceMappingURL` (el mapa no se aloja y provocaría una petición 404). `fonts.css` se generó (un `@font-face` por peso y subconjunto, con `font-display: swap`).

### Huellas SHA-256 de lo que hay en el repositorio

| Archivo (bajo `static/vendor/`) | Tamaño | SHA-256 |
|---|---|---|
| `bootstrap/bootstrap.min.css` | 227.3 KB | `26db49828d6701fcfce37a96da6ec3f0ed481abae49c8c9969a575b064413cad` |
| `fontawesome/css/all.min.css` | 99.6 KB | `1edb1725a9ea8ca4dcf2f5508cee183218aa1685e47c1b23056717f754f58ebf` |
| `fontawesome/webfonts/fa-brands-400.woff2` | 105.5 KB | `748332090c4b8e20f95d0ff59f0be20fa9c889359d3b36d4b886d73376054207` |
| `fontawesome/webfonts/fa-regular-400.woff2` | 24.4 KB | `8e7e5ea1b15f62ab14dbd41768e8fbcd21cc859a4ea5da812457ee714299fb35` |
| `fontawesome/webfonts/fa-solid-900.woff2` | 146.6 KB | `7152a6933ee3d690ec2af3d09da9d701723d16aa3410a6d80f28ff8866f3b880` |
| `fontawesome/webfonts/fa-v4compatibility.woff2` | 4.5 KB | `694a17c3d9d6c05f8aac63c544615552a4b220e9a4de863d87341a6bcfc1bc8d` |
| `fonts/fonts.css` | 7.5 KB | `09e2ec78d484cea8c29203e263e18f30d2eec17265793d3fdf4fd1afab7fc5ef` |
| `fonts/jetbrains-mono-latin-400-normal.woff2` | 20.7 KB | `14425ba9c695763c1547f48a206b7aa60350a33ae23de09f0407877f3fcd89eb` |
| `fonts/jetbrains-mono-latin-500-normal.woff2` | 21.3 KB | `cb182feeed4d798ff6961d3c79f7026279448fca0676438aaecb21f3fc39553a` |
| `fonts/jetbrains-mono-latin-600-normal.woff2` | 21.3 KB | `400c6bfda18d5d14acad1c15d6dcb9f8e13c015e7286317e0b9a482539bef147` |
| `fonts/jetbrains-mono-latin-700-normal.woff2` | 21.4 KB | `d0d4e818808f2a0ba39b2b09d1989366f63494e295f003c7ef436697378507e8` |
| `fonts/jetbrains-mono-latin-ext-400-normal.woff2` | 7.2 KB | `505dfba8ecbe77e82765f36d317ed7ef4ac42719dc5f4ae68d1c483fd22d0d14` |
| `fonts/jetbrains-mono-latin-ext-500-normal.woff2` | 7.4 KB | `879df9319f1cbf633bee1dd489e376a9e1e8c458f4abddcfe381cb83b5e6b027` |
| `fonts/jetbrains-mono-latin-ext-600-normal.woff2` | 7.3 KB | `90899aac1c15552c028fd205376bb537a2a5205164fe8626efabad77d7260c20` |
| `fonts/jetbrains-mono-latin-ext-700-normal.woff2` | 7.3 KB | `45a606d88f72bc0afae4d1a6988e3dba3a5e77ee51aa3b550e37c31c728f4d2d` |
| `fonts/plus-jakarta-sans-latin-300-normal.woff2` | 11.9 KB | `740d9b0f5a33987e21acff7e20bbd4c02bf40e470cda58b761127d159c7941a7` |
| `fonts/plus-jakarta-sans-latin-400-normal.woff2` | 11.5 KB | `221a4135d06a4b33abbd535e9a0da4e565d19545ddd9267c4e678a916d54d9b6` |
| `fonts/plus-jakarta-sans-latin-500-normal.woff2` | 12.0 KB | `bad081c8dbc15aed6c5e4cde5461914f7b3bdc295b7a7aed177e4beddb79bffa` |
| `fonts/plus-jakarta-sans-latin-600-normal.woff2` | 11.9 KB | `8872bb5c9111df9bd3162c2394aee6354d782b13408a4dd5bbf65c48207206e6` |
| `fonts/plus-jakarta-sans-latin-700-normal.woff2` | 12.0 KB | `2050755bf475817c96ac7d914c7f07cc3c2d11ff4b3fb4747b8d41de584aad17` |
| `fonts/plus-jakarta-sans-latin-800-normal.woff2` | 11.6 KB | `5f301a8ef9c266c8b596e6793d3cc826daeff9849c5af035f5386439137955ad` |
| `fonts/plus-jakarta-sans-latin-ext-300-normal.woff2` | 10.6 KB | `3bfbc7278a6723bc895a1c088f8c216a79c21a9c5c227c7b6ed194c58e12fb53` |
| `fonts/plus-jakarta-sans-latin-ext-400-normal.woff2` | 10.3 KB | `65b5680bf2ba9c6c42af74ddd2e8aadde93af7c523454ebb3d4e32fdba3f94db` |
| `fonts/plus-jakarta-sans-latin-ext-500-normal.woff2` | 10.6 KB | `d1584f50c388cae7e570bed35be331fa4a9afd6832ef45a32fa8bf81930e6db9` |
| `fonts/plus-jakarta-sans-latin-ext-600-normal.woff2` | 10.6 KB | `080a1fea8589c2bd4fa08750d0d99b3388be63df8c41a649de05cb2dda1ec007` |
| `fonts/plus-jakarta-sans-latin-ext-700-normal.woff2` | 10.8 KB | `b202ec87899e78825db955e6ab43858b88c0cd444abe2dfac2e763f837b8f234` |
| `fonts/plus-jakarta-sans-latin-ext-800-normal.woff2` | 10.6 KB | `7c27e3fd36e9c1d6a2f354943b32993b01dc0b1e64e8275bdd5122b260ca6a24` |

## Cómo actualizar una biblioteca

1. Descarga la versión nueva del mismo origen y **verifica la huella publicada** (SRI de la documentación de Bootstrap o de cdnjs) antes de guardarla.
2. Sustituye los archivos, actualiza la versión y las huellas de este documento.
3. Si la hoja referencia archivos (`url(...)`), alójalos también. `FrontendTemplatesComplianceTest` falla si falta alguno.
4. Revisa el aspecto de las pantallas en un navegador: las pruebas comprueban la CSP, no que "se vea bien".
5. Mantén las versiones fijadas (nunca `latest`) y revisa los avisos de seguridad de la biblioteca (Bootstrap y Font Awesome publican boletines).

## Cómo se protege

| Control | Dónde |
|---|---|
| `/vendor/**` es público (el login lo necesita antes de iniciar sesión) y de solo lectura | `SecurityConfiguration` |
| Los archivos estáticos no consumen el cupo de peticiones de la IP (una página pide unos 20) | `RateLimitCategory.isStaticResource` |
| No se sirve el listado de directorios (`/vendor`, `/css/`, `/js/`...): se deniega toda ruta bajo una carpeta estática cuyo último tramo no tenga extensión | `SecurityConfiguration.isStaticDirectoryRequest` |
| Ninguna plantilla tiene scripts o estilos en línea, manejadores `on*`, recursos externos, identificadores numéricos ni formularios POST sin `th:action`; el cierre de sesión es un `POST /logout`; todas las referencias estáticas existen | `FrontendTemplatesComplianceTest` |
| `/vendor/**` accesible sin sesión, tipos de contenido correctos, sin 429 y sin acceso a otros archivos | `VendorAssetsIntegrationTest` |

## Qué cambió al integrar el frontend (`Correcion-frontend`, commit `256ad09`)

La rama se separó de `main` el 3 de octubre, antes del trabajo de seguridad. Se tomó **el diseño** de la rama y **la seguridad** de `main`:

- Se conservó de `main`: identificadores públicos (`publicId`), token CSRF (`th:action`), cierre de sesión por `POST /logout`, fragmento `security-head`, `th:if="${canManageProjects}"`, confirmaciones por `data-confirm-message`, formularios con `th:object`/`th:field` y validación, la acción "Escanear red WiFi" del panel y los scripts propios (`area-filter.js`, `project-filter.js`, `project-list-filter.js`, `vulnerability-form.js`).
- Se tomó de la rama: la hoja `style.css` rediseñada, `responsive-tablet.css`, el logo optimizado y la estructura de las plantillas (cuadrícula de Bootstrap, `table-responsive`, clases `btn`, sección de bienvenida del panel, nueva pantalla de acceso).
- Se añadieron a la hoja nueva las 17 clases que `main` usa y la rama no define (`.inline-form`, `.no-underline`, `.progreso-*`...), en la sección "Adaptaciones de seguridad".
- Los scripts en línea pasaron a `login-effects.js` y `dashboard-greeting.js`.
- La rama traía una pantalla de acceso con elementos que **no existen**: ver el apartado de decisiones pendientes del registro de desviaciones.

### Defectos de `main` corregidos de paso

Font Awesome no se cargaba en ninguna página de `main` (76 iconos invisibles) y `fa-sparkles` no existe en la versión gratuita (se usa `fa-wand-magic-sparkles`). Además, la primera línea de `style.css` de la rama era un `@import` a Google Fonts, que la CSP bloquea y que avisaba a un tercero de cada visita; ahora importa `/vendor/fonts/fonts.css`.

Nota sobre identificadores: en `detalleproyecto.html`, `proyecto` y `dep` son campos de `ProyectoView`, cuyo `id` **ya es el identificador público**. Por eso `proyecto.id` es correcto allí aunque en una entidad `getId()` devuelva el número interno. `FrontendTemplatesComplianceTest` prohíbe el id numérico solo en las variables que son entidades.
