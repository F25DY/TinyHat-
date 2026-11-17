# Etapa 02 — Modelo de informes

Sobrescribe el módulo de informes anterior (decisión D1) con el modelo del plan: serie → versiones → archivos.
**El módulo de informes de red no se tocó** (`com.hexora.network.*`, `network-report.*`): ningún archivo suyo aparece en el diff.

## Hecho
- **Migración `V32__create_informes_model.sql`:** elimina la tabla `informe` anterior (V22/V28, sin datos que conservar) y crea `informe`, `informe_version` e `informe_archivo`.
  - Mismo patrón de aislamiento del esquema existente: `cuenta_id` en cada tabla y **claves foráneas compuestas** (proyecto + organización + cuenta, usuario + cuenta, informe + cuenta, versión + cuenta). Una fila no puede apuntar a un proyecto, usuario o informe de otra cuenta u organización.
  - **I1 e I2 impuestas por la propia base:** columnas generadas con índice único dejan como máximo un BORRADOR y un PUBLICADO por serie.
  - `CHECK` de tipo, alcance, estado, número, longitud de SHA-256 y de **coherencia por estado** (un borrador no tiene snapshot; un publicado exige snapshot, hash, plantilla y fecha; un despublicado exige además fecha y motivo).
  - `narrativa_json` y `snapshot_json` son `JSON` nativo.
- **Enums** (`com.hexora.hexora.informes`): `TipoInforme` (lista blanca tipo × formato de §1.2), `FormatoInforme` (MIME y extensión exactos), `EstadoVersion` (solo BORRADOR→PUBLICADO y PUBLICADO→DESPUBLICADO).
- **Entidades** (`model`): `Informe`, `InformeVersion`, `InformeArchivo`. En `InformeVersion` no hay setters de estado ni de snapshot: todo pasa por `publicar`, `despublicar` y `actualizarNarrativa`; un `@PreRemove` impide eliminar lo publicado o despublicado (I3/I5); `@Version` da bloqueo optimista.
- **Repositorios filtrados dentro de la consulta:** `findVisibleByPublicId` (versiones y archivos), `findVisibleByProyecto`, `findPublicadasDeOrganizacion`, `findPublicadasDeProyecto`. Regla: AUDITOR solo en proyectos asignados (ve todo); CLIENTE_ADMIN solo versiones PUBLICADAS de su organización; otra cuenta, otra organización u otro rol → vacío (404).
- **`PermissionService`:** `requireInformeVersion` y `requireInformeArchivo` reemplazan a `requireInforme`.
- **Retirado del módulo anterior:** paquete `report` completo (servicio, controlador REST `/api/...informes`, ensamblador, renderizador sin implementar, matriz de 4 niveles), panel "Informes" de `detalleproyecto.html`, `project-reports.js/.css`, sus reglas en `SecurityConfiguration` y la ruta `/api/informes/*/descarga` de `RateLimitCategory`.

## Archivos tocados
- Nuevos: `V32__create_informes_model.sql`; `informes/{TipoInforme,FormatoInforme,EstadoVersion}.java`; `model/{InformeVersion,InformeArchivo}.java`; `repository/{InformeVersionRepository,InformeArchivoRepository}.java`; pruebas `TipoInformeTest`, `InformeVersionTest`, `InformesModeloIntegrationTest`.
- Reescritos: `model/Informe.java`, `repository/InformeRepository.java`, `service/PermissionService.java`.
- Ajustados: `SecurityConfiguration`, `RateLimitCategory`, `detalleproyecto.html`, `style.css` (solo reglas `.reports-*`), `DashboardIntegrationTest`, `PhaseTwelveAuditIntegrationTest`, `fase12-herramientas.mjs`.
- Eliminados: `report/*` (main y test), `project-reports.css`, `project-reports.js`.

## Pruebas (comando + resultado)
- `mvnw test` (suite completa): **388 pruebas, 0 fallos, 0 errores.** Nuevas: 57 (`TipoInformeTest` 20, `InformeVersionTest` 21, `InformesModeloIntegrationTest` 16). Retiradas: las del módulo anterior (`ReportFoundationIntegrationTest`, `RiskMatrixCalculatorTest`).
- `InformesModeloIntegrationTest` (MySQL real, Testcontainers) cubre: tablas, índices únicos, `CHECK` y claves foráneas existentes; I1 e I2; cada restricción rechaza lo que debe **por su código de error de MySQL** (3819 check, 1062 duplicado, 1452 clave foránea) a partir de una fila de control válida; cruce de cuentas y organizaciones imposible; ida y vuelta de JSON con ñ, tildes y ✓; bloqueo optimista; lo publicado no se elimina y un borrador sí; narrativa precargable (I6); y la matriz de visibilidad completa (auditor asignado / otro auditor / auditor de otra cuenta / cliente de su organización / cliente de otra organización / cliente de otra cuenta / rol desconocido) para versiones, archivos y listados, incluidos los 404 de `PermissionService` con el filtro de cuenta de Hibernate activo.

## Criterios de aceptación
- ✔ Restricciones e índices creados (verificados contra `information_schema`).
- ✔ Pruebas de repositorio con aislamiento.

## Desviaciones y decisiones pendientes
- **D17 (temporal, necesita tu visto bueno):** la prueba de la Fase 12 exige que `REPORT_PUBLISHED`, `REPORT_VIEWED` y `REPORT_EXPORTED` tengan un emisor, y era el servicio retirado. Agregué una **excepción explícita y autovigilada** en `PhaseTwelveAuditIntegrationTest`: esos tres eventos deben seguir sin emisor hasta la Etapa 6; si alguien los emite antes, la prueba falla y obliga a quitar la excepción. En la Etapa 6 los emitiré (o los renombraré a `INFORME_*` actualizando la lista del plan de seguridad); dime tu preferencia.
- **D18:** hasta la Etapa 7 la página del proyecto **no tiene entrada a Informes** (el panel viejo dependía de la API retirada).
- **D19:** nombres de columnas según el esquema existente (`public_id`, `cuenta_id`, `proyecto_id`, `creado_por`) en lugar de los orientativos del plan (`uuid`, etc.). Se agregó `cuenta_id` a las tres tablas (patrón multi-cuenta del proyecto).
- Siguen sin confirmar los defaults D8/D15 (`INFORMES_PDF`, ampliar `Notifier` en la Etapa 6, openhtmltopdf 1.1.114).

## Riesgos o deuda detectada
- La inmutabilidad de lo publicado (I3) la imponen la entidad y los `CHECK` de coherencia, pero un `UPDATE` directo por SQL sobre un publicado no lo bloquea la base (no hay disparadores). Se podría agregar con `TRIGGER` si lo quieres como defensa extra; el proyecto hoy usa permisos de BD (p. ej. `audit_event` solo-insertar), no disparadores.
- `OrderByTipoAsc` ordena por el texto del código (EJE antes que TEC); la interfaz deberá ordenar por el orden de negocio.
- Hasta la Etapa 6 las capacidades (`INFORMES_PDF`), el rate limit y los eventos de auditoría del módulo nuevo aún no existen.

ESTADO: EN ESPERA DE "APROBADA ETAPA 02"
