# Security implementation deviations

## 2026-11-09 — Inicio de Fase 10

- Se asignó V27 a la Fase 10 para rangos de red autorizados por proyecto, bloqueos de escaneo y la asociación opcional de hosts existentes a un proyecto. La migración solo se validó en una base desechable mediante las pruebas web de red; no se aplicó a la BD compartida.
- Se inició la protección fail-closed de escaneos y descubrimientos: destino IPv4, interfaz y rango vigente deben coincidir con el proyecto; se exige la capacidad `modulo_red` y se agregaron límites por prefijo y bloqueos/cooldown persistentes. Se endureció el handshake WebSocket para requerir sesión autenticada, MFA y capacidad de red, y se desactivó el descubrimiento automático que no puede demostrar un proyecto autorizado.
- Se corrigió el orden de validación de hosts: un host fuera de la allowlist conserva el resultado de no encontrado antes de que se compruebe el vínculo a proyecto. Las pruebas focalizadas de red pasan: 39 pruebas, 0 fallos, 0 errores.
- Ver [reporte de avance de Fase 10](./reports/fase-10.md). No ejecutar V27 contra la BD compartida sin respaldo, aprobación y ventana de migración.

## 2026-11-09 — Continuación de Fase 10: aislamiento por proyecto

- Se completaron la selección del proyecto visible, declaración/listado de rangos y el envío del proyecto en las operaciones principales de monitorización. Las lecturas de inventario, gateway, snapshots y eventos de detección consultan el proyecto asociado; los eventos WebSocket revalidan permisos al entregar cada actualización.
- V27 también separa inventario detectado e historial mediante `proyecto_id`. Los datos históricos ambiguos permanecen sin proyecto y no se exponen por coincidencia de dirección/subred. La migración y el nuevo aislamiento se verificaron en la base MySQL desechable; no se ejecutó en la base compartida.
- El estado local del navegador se separa por proyecto y la interfaz limpia los datos visibles al cambiar de selección. Se agregaron pruebas contra la filtración entre proyectos que autorizan la misma subred.
- Las pruebas ejercitan concurrencia y expiración del cooldown con MySQL desechable, y controles WebSocket de MFA, origen wildcard, tamaño/mensajes, terminación de sesión y revalidación de permisos. Se añadieron también rechazos cross-project de lectura, mutación/borrado de hosts y puertos, historial de escaneos y limpieza de eventos.
- Las rutas HTTP de escaneo y descubrimiento registran resultados de éxito, rechazo por alcance/cooldown y errores de descubrimiento.
- Regresión focalizada: **96 pruebas, 0 fallos, 0 errores y 1 omitida; BUILD SUCCESS**. `node --check` pasó en ambos scripts de monitorización.
- Fase 10 **sigue en curso y no está aprobada**. Pendientes: cobertura extendida de locks y handshake real, instrumentación del escáner sin tráfico, auditoría de scheduler/retención y revisión de paginación del historial. El scheduler no debe descubrir dispositivos automáticamente hasta completar el contexto de proyecto y sus controles.

## 2026-11-09 — Decisión de Fase 9 y validación focalizada de SMTP

- El documento rector adjunto define Fase 9 como OAuth2/OIDC con `identidad_externa`, PKCE, asociación explícita y MFA; SMTP no es su alcance.
- Por decisión del usuario, no se incorporará OAuth2/OIDC: se prioriza mantener el inicio de sesión actual con MFA obligatorio y evitar sumar otra frontera de autenticación sin necesidad. No se implementó flujo OAuth ni se creó infraestructura adicional.
- La implementación SMTP se conserva como trabajo complementario iniciado por una interpretación incorrecta de Fase 9. No debe contarse como fase aprobada ni como cumplimiento de F9-CA1–F9-CA8.
- La validación focalizada SMTP pasó: `SmtpNotifierTest`, `ProfileSecurityGuardMfaTest` y `ProfileSecurityGuardConsoleNotifierTest`, **10 pruebas, 0 fallos, 0 errores**. Se verificaron los cuatro mensajes, contenido UTF-8/enlaces, propagación de fallos de envío y la aceptación/rechazo de guardas SMTP para `prod`. Son pruebas unitarias y de configuración; no se configuró un relay real ni se confirmó entrega externa.
- El documento reserva V26 para identidades externas, pero V26 ya fue usada por Fase 8 para tokens y revocación persistente (V24 ya correspondía a normalización de puertos y V25 a MFA). No editar V26. V27 se asignó posteriormente a Fase 10; si en el futuro se reconsidera OAuth, revisar primero el mapa de migraciones y usar la siguiente versión libre.
- Fase 0 tampoco tiene el `00-inventario.md` requerido en el árbol revisado; Fase 4 y la confirmación de cuota oficial NVD en Fase 6 conservan salvedades en sus informes. La suite actual pasó con 143 pruebas, 0 fallos, 0 errores y 1 omitida; ello no cierra esos puntos documentales/de alcance.
- Ver [reporte corregido de Fase 9](./reports/fase-09.md) para la decisión de no incorporar OAuth y la revisión de Fases 0–8.

## 2026-11-09 — Fase 8: registro, invitaciones y recuperación

- Se completaron rutas y servicios para registro/verificación, invitaciones a organización, recuperación de contraseña y operaciones CLI de propietario. Se ajustó el consumo atómico de tokens para filtrar también por tipo y se corrigió el uso del `Clock` inyectado.
- Los enlaces dependen de `PUBLIC_BASE_URL` en vez del `Host` recibido; producción requiere HTTPS. Las operaciones de propietario exigen `--tinyhat.ops.enabled=true` y `spring.main.web-application-type=none`.
- Fase 8 usa V26, pues V25 ya se asignó a MFA. V26 crea tokens de seguridad y el marcador persistente `cuenta.sesiones_revocadas_en`, requerido para revocar sesiones entre instancias. OAuth/OIDC no se implementará. V27 se asignó posteriormente a Fase 10. Flyway validó y aplicó V26 en un MySQL 8.0.46 desechable de Testcontainers; no se tocó la BD compartida.
- Las 9 pruebas de integración Fase 8 pasaron. En esa validación también pasó la suite completa: 136 pruebas, 0 fallos, 0 errores y 1 omitida. La validación completa de migración histórica hasta V25 y la seguridad de Fase 3 también pasan. La persistencia del marcador permite rechazar sesiones no registradas localmente tras reactivación. SMTP no era parte del alcance de Fase 8 ni de Fase 9 OAuth; `PUBLIC_BASE_URL` debe configurarse para el dominio HTTPS real.
- Ver [reporte de Fase 8](./reports/fase-08.md) para criterios, alcance y resultados de validación.

## 2026-11-09 — Fase 7: MFA obligatorio con TOTP

- Se implementó la inscripción y verificación TOTP con secreto de 160 bits cifrado AES-256-GCM, prevención de replay, puerta PRE_MFA y rotación de sesión al completar el segundo factor.
- Se añadieron diez códigos de recuperación de un solo uso con hashes BCrypt y reautenticación con contraseña + TOTP para el reinicio MFA, con evento `MFA_RESET` e invalidación de sesiones.
- Se creó V25 porque V24 ya está aplicada para la normalización de puertos. V25 se validó únicamente con MySQL desechable; no se ejecutó en la base compartida.
- Las pruebas de fases anteriores conservan su alcance configurando MFA desactivado solo en esos contextos. La clave sintética de pruebas se proporciona por Surefire, sin sobrescribir el `application.properties` principal.
- Validación: suite completa con 131 pruebas, 0 fallos, 0 errores y 1 omitida; `git diff --check` sin errores.
- Ver [reporte de Fase 7](./reports/fase-07.md) para criterios, configuración y límites. No se inició Fase 8.

## 2026-11-09 — Fase 6: intentos y rate limit

- Añadidos `bucket4j_jdk17-core:8.19.0` y Caffeine `3.3.1`; el artefacto `bucket4j_jdk17` inspeccionado inicialmente era un POM agregador, por lo que se corrigió a la biblioteca core con JAR.
- `IpRateLimitFilter` se aplica al principio de las cadenas de seguridad; `X-Forwarded-For` solo se procesa si el peer directo coincide con `tinyhat.security.trusted-proxies`. `UserRateLimitFilter` aplica los buckets por usuario y categoría. Los rechazos entregan 429/`Retry-After`/`RATE_LIMITED` y la auditoría se agrega a una fila por sujeto/categoría/minuto.
- `LoginAttemptService` mantiene contadores temporales en Caffeine para email normalizado (incluidos emails inexistentes) e IP; aplica el calendario y ventana configurados. Éxito invalida solo el contador del email. Las solicitudes bloqueadas mantienen el mismo 302 a `/login?error` que un fallo de autenticación, incluso cuando coincide con agotamiento del bucket de login.
- Las consultas externas de búsqueda y detalle CVE comparten un bucket NVD, después de la consulta local. Se pueden probar mediante `tinyhat.nvd.base-url` con un servidor HTTP simulado.
- Añadidos límites de cabeceras, formularios, multipart y conexión Tomcat. Los filtros y contadores son locales al proceso; no coordinan réplicas. Las cuotas oficiales de NVD no pudieron verificarse porque el sitio entregó una página SPA sin contenido documental.
- La clase de rutas MFA queda limitada, pero aún no existe flujo TOTP; F6-CA5 requiere la Fase 7 y queda documentado como pendiente. Tampoco se inventaron ni afirmaron implementados los futuros endpoints OAuth, invitaciones o verificación de correo.
- Validación de Fase 6: suite completa con 122 pruebas, 0 fallos, 0 errores y 1 omitida. Sin migraciones ni escrituras a la BD compartida y sin cambios a `main`.
- Ver [reporte de Fase 6](./reports/fase-06.md) para criterios, limitaciones y prueba NVD simulada.

## 2026-11-08 — V18 recovery and partial database state

- The shared MySQL database recorded V18 as failed after the account table and organization rename/columns had been created. The existing auditor-to-organization foreign key uses a server-generated name rather than the name assumed by the migration.
- V18 now resumes the existing `cuenta` and `organizacion_cliente` setup without duplicating it, and discovers the actual auditoria, area, and vulnerability FK names from `information_schema`.
- Before recovery, a fresh snapshot was saved as `C:\Users\Fredy\OneDrive\Documentos\dumps\Dump20261108_pre_recovery.sql`. The user-provided `Dump20261108.sql` was inspected read-only; it records V18 with `success=0` and includes the same partial state, so it was not imported.
- Flyway repair removed only the failed V18 history row, then V18 was applied to the shared `hexora_db` with the target explicitly limited to 18.
- Before applying the remaining existing migrations, a second snapshot was saved as `C:\Users\Fredy\OneDrive\Documentos\dumps\Dump20261108_pre_v19_v24.sql`. A disposable MySQL restore successfully migrated from V18 through V24.
- Flyway then applied V19–V24 to the shared database, with target explicitly limited to 24. The database is now at V24, and every recorded version succeeds. V24 copied the host port list to `monitored_port` and removed the redundant `network_host.monitored_ports` column.
- Verification confirmed the original row counts were preserved, public IDs were populated, and checked cross-account relationships return zero. V18 established 24 account-scoped FKs; V19–V23 added user, report, and audit-event relationships, bringing the final count to 36. The four configured host ports (21–24) remain enabled in normalized rows.
- V19 creates the `usuario` structure but no users were inserted. V21 created three default capabilities for the existing account; V22 and V23 tables are empty as expected.
- Canonical `network_asset` unification and the optional CVE-catalog link with manual CVE entry remain a separate schema-design task. No future migration (V25+) was created or applied.

## 2026-11-08 — Normalized monitored-port migration

- V24 is currently used to copy the legacy `network_host.monitored_ports` values into `monitored_port`, then drop the redundant text column.
- The original roadmap reserved V24 for MFA; the migration map is displaced. MFA must use V25 or a later unused version, with subsequent versions assigned after it.
- Canonical network-asset unification and account-scoped composite relationships across the network tables remain incomplete. Do not consider the network schema redesign complete or apply it to the shared database.

## 2026-11-07 — Integration-test datasource isolation

- The context-load and network web integration tests now use a shared disposable MySQL Testcontainer instead of inheriting the developer's `.env` datasource.
- Network web tests now provide an authenticated auditor and CSRF tokens for state-changing requests while retaining the security filters.

## 2026-11-08 — Phase 4 tenant isolation

- Tenant-scoped entity mappings now use the account filter and persistence listener; authenticated HTTP requests establish the account from `TinyHatPrincipal`, and the filter is disabled when the request completes.
- Project, area, and finding flows use public UUIDs, validate auditor assignment or client-organization visibility, and return a scoped not-found response for inaccessible records.
- Network scheduled tasks execute independently for each active account. History cleanup and retention operations include explicit account predicates, and WebSocket broadcasts are limited to authenticated sessions in the active account.
- Added isolated integration coverage for account-scoped key lookups and writes, project visibility, history cleanup, scheduler execution, WebSocket broadcast, and the HTTP tenant filter.
- The full test suite passed with 100 tests, no failures or errors, and one intentionally skipped test. Tests use disposable MySQL containers; no migration was applied to the shared database, and `main` was not changed.
- Network API routes still use numeric resource IDs. Their database access remains tenant-scoped; switching those routes to public UUIDs is not part of this phase's implementation.
- See `reports/fase-04.md` for criteria, test coverage, and remaining scope notes.

## 2026-11-08 — Phase 5 session lifecycle

- Session cookies use `TINYHAT_SID`, `HttpOnly`, `Secure`, `SameSite=Strict`, and cookie-only tracking. The servlet timeout is a 10-minute fallback; `SessionActivityFilter` enforces five-minute user inactivity and the 120-minute absolute lifetime with an injectable `Clock`.
- Polling paths are configured server-side and do not refresh user activity. Interactive requests update the last-activity timestamp.
- Login rotates the session ID and initializes its timestamps. A second login replaces the previous session; APIs receive `SESSION_REPLACED`, while web requests go to `/login?replaced`.
- Each authenticated request rechecks account/user status, role, and organization. Suspended or changed principals have their sessions expired and audited; session destruction publishes an internal event that closes associated WebSockets.
- Integration/unit coverage verifies cookie attributes on embedded HTTP, session rotation/replacement, inactivity and absolute expiry, polling, suspension, URL tracking behavior, and WebSocket cleanup.
- Full suite: 110 tests, 0 failures, 0 errors, 1 skipped. Test databases use disposable MySQL containers. No migration or write was applied to the shared database, and `main` was not changed.
- See `reports/fase-05.md`; stop for user review and approval before starting Fase 6.
