# Etapa 07 — Controladores y vistas

Las vistas de §9 sobre `InformeService`. Antes de empezar se atendieron tres encargos de la aprobación de las Etapas 5 y 6: los disparadores de la base (D24), el renombre de `VistaPdf` (D32) y la reordenación de la publicación que los disparadores exigen.

## Hecho
- **Disparadores V33 (cierra D24):** la base impide editar o borrar lo publicado.
  - `informe_version`: una DESPUBLICADA no se modifica; una PUBLICADA solo puede pasar a DESPUBLICADA y sin tocar contenido, snapshot, hash, plantilla ni fechas de publicación; un BORRADOR no puede despublicarse; solo un BORRADOR se borra.
  - `informe_archivo`: solo se inserta para versiones PUBLICADAS o DESPUBLICADAS (I4) y nunca se actualiza ni se borra.
  - La publicación se reordenó: mueve los archivos, **publica la versión** y solo entonces registra los archivos.
  - Requieren `TRIGGER` y, con registro binario, `log_bin_trust_function_creators=1` (puesto en `docker-compose.yml` y en los contenedores de prueba; ver RUNBOOK).
- **Renombre:** `VistaPdf` → `VistaInforme` y `ConstructorVistaPdf` → `ConstructorVistaInforme` (cierra la deuda de D32).
- **Controlador `InformeWebController`** con las rutas de §9:
  - AUDITOR: `GET /proyectos/{p}/informes`, `POST /proyectos/{p}/informes/{tipo}/borradores`, `GET /informes/versiones/{v}`, `POST …/narrativa`, `GET …/vista-previa`, `POST …/generar`, `POST …/publicar`, `POST …/despublicar`, `POST …/eliminar`.
  - AUDITOR y CLIENTE_ADMIN: `GET /informes/archivos/{a}` y `GET /informes/versiones/{v}/verificacion`.
  - CLIENTE_ADMIN: `GET /cliente/informes`.
  - Todo con `@PreAuthorize`, reglas en `SecurityConfiguration` (la última sigue siendo `denyAll`), UUID públicos validados (`@Pattern`), `@Size`/`@NotBlank` en narrativa y motivo (10–500), tipo y formato como enum, CSRF en todos los POST.
- **Vistas** (`templates/informes/`): lista por tipo con versiones y acciones permitidas por estado; editor con una `textarea` por sección, **condiciones pendientes de publicar**, solo los formatos válidos del tipo (§1.2), vista previa, copias NP, publicar, eliminar y despublicar (diálogo con motivo); verificación (ID, hashes, fechas, instrucción de `sha256sum`); bandeja del cliente. Menú lateral «Informes» para el CLIENTE_ADMIN y botón «Informes» en el detalle del proyecto.
- **Vista previa HTML:** las mismas plantillas del PDF con la marca NP, con el estilo servido como archivo estático (`/css/informe-vista-previa.css`) y el logo existente; sin estilos ni scripts en línea.
- **Descargas** (archivo publicado y copia NP): `Content-Type` exacto, `Content-Disposition: attachment; filename="{ID}.{ext}"`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`. La del archivo publicado verifica antes el SHA-256.
- **Errores genéricos con correlationId:** 503 (cupo lleno, con `Retry-After`), 429 (límite, con `Retry-After`), 500 (integridad o fallo técnico), 409; recurso ajeno o no visible → 404; entrada inválida → 400.
- **`InformeService`:** nuevos `listar`, `detalle`, `listarPublicadas`, `previsualizarHtml` y `generarCopia`.

## Archivos tocados
- Nuevos: `V33__informes_immutability_triggers.sql`, `controller/{InformeWebController,InformeExceptionHandler}`, `render/pdf/VistaPreviaInforme`, `templates/informes/{lista,editor,verificacion,cliente}.html`, `static/css/{informes,informe-vista-previa}.css`.
- Modificados: `SecurityConfiguration` (rutas), `CurrentUserAdvice` y `sidebar.html` (menú del cliente), `detalleproyecto.html` (botón Informes), `InformeService`, `docker-compose.yml`, los cinco contenedores de prueba de MySQL, `InformesModeloIntegrationTest`.

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **783 pruebas, 0 fallos, 0 errores** (ver Etapa 8).
- **Disparadores (4 nuevas en `InformesModeloIntegrationTest`):** una PUBLICADA rechaza editar narrativa, hash, plantilla o número y borrarse; PUBLICADA → BORRADOR rechazado; PUBLICADA → DESPUBLICADA con motivo permitido y luego inmutable (incluido volver a PUBLICADA y borrar); el borrador sí se edita y se borra; un archivo no se inserta para un borrador y no se actualiza ni se borra.
- **Web (5 en `InformeWebIntegrationTest`, sesión real y CSRF):**
  - Recorrido completo: lista → crear borrador → editor con condiciones pendientes → narrativa (el `<script>` escrito aparece escapado) → vista previa HTML con la marca NP, sin `classpath:` ni scripts → copia DOCX NP con las cuatro cabeceras → el cliente no ve el borrador → publicar PDF y DOCX → bandeja del cliente → descargas con `attachment`, `nosniff` y `no-store` → verificación para ambos roles → despublicar (motivo corto → 400) → el cliente recibe 404 en bandeja, archivos y verificación y el auditor conserva el acceso.
  - Sin sesión → login; sin CSRF → 403; CLIENTE_ADMIN, AUDITOR sin asignación y AUDITOR de otra cuenta → 403/404 en crear, editar, generar, publicar, despublicar, eliminar, vista previa, verificación y lista.
  - Entradas inválidas (UUID, tipo, formato, motivo y texto demasiado largos) → 400; un formato que el tipo no admite no publica nada.
  - Límite de generación: la sexta operación responde 429 con `Retry-After` y sin detalles internos.
  - **CSP:** las páginas de lista, editor y bandeja no tienen atributos `style`, bloques `<style>`, manejadores `on*` ni scripts sin `src`.

## Criterios de aceptación
- ✔ Pruebas de autorización de §11 en verde.
- ✔ Cabeceras de descarga correctas.
- ✔ La CSP no se relaja (no se tocó; las pruebas comprueban que las vistas no usan estilos ni scripts en línea).
- ✗ → **tuyo:** revisión visual de las vistas (no hay navegador en este entorno).

## Desviaciones y decisiones pendientes
- **D41:** la clase de límite `informe-generacion` se aplica desde el servicio (ya en D37); la clasificación por ruta del filtro de IP **no** se agregó: el límite por usuario ya cubre vista previa, generar y publicar.
- **D42:** la vista previa es **HTML con hoja de estilos propia**, no el PDF incrustado; el PDF sigue siendo la referencia.
- **D43:** el CLIENTE_ADMIN conserva el resto del menú lateral actual (Panel, Escaneo de red), que ya le estaba vedado; solo se agregó «Informes». Ordenar el menú por rol queda fuera de este componente.
- Cierra D24 (disparadores) y D32 (nombre `VistaPdf`).

## Riesgos o deuda detectada
- Los disparadores añaden un requisito de despliegue (`TRIGGER` / `log_bin_trust_function_creators`); está en el RUNBOOK.
- La interfaz usa clases del sitio existentes y un CSS pequeño nuevo; conviene tu revisión visual (alineación, tablas anchas en móvil).

ESTADO: EN ESPERA DE "APROBADA ETAPA 07"
