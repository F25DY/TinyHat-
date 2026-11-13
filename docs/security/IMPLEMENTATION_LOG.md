# Security implementation deviations

## 2026-11-13 — Fase 13 (pipeline CI/CD)

- **Inicio.** La persona aprobó la Fase 12 de palabra y pidió continuar con la Fase 13. La función de creación de informes queda en espera hasta diseñar su flujo; los cimientos de la Fase 12 se conservan. Respaldo previo en `dumps\fase-13\20261113_114322\`. Rama `seguridad/fase-13` creada desde `seguridad/fase-11`, que ya contenía el commit "Desarrollo hasta fase 12" de la persona.
- **Sin migraciones.** `hexora_db` sigue en V28.
- **Workflow `.github/workflows/security.yml`.** Jobs: pruebas (`mvnw clean verify`), secretos, dependencias, SAST SpotBugs y SAST CodeQL. Permisos `contents: read`; sin `pull_request_target`, `workflow_dispatch`, despliegue ni escritura. Acciones fijadas por SHA de commit completo (`actions/checkout` v7.0.1, `actions/setup-java` v6.2.0, `actions/upload-artifact` v7.0.2, `actions/cache` v6.1.0, `github/codeql-action` v4.39.3 con el commit pelado de su etiqueta anotada).
- **gitleaks como imagen fijada por huella** (8.30.1) y no como acción de marketplace: sin licencia ni dependencia extra. El análisis de historial corre en cada ejecución y no "una sola vez" como pedía el plan; hoy está limpio. Línea base opcional en `.gitleaks-baseline.json`.
- **`.gitleaks.toml`.** Reglas por defecto más una única excepción: la regla `generic-api-key` bajo `hexora/src/test/java`, donde viven valores ficticios (regla R9). El historial pasó de 11 hallazgos a 0 y no había secretos reales versionados: `hexora/.env` y `hexora/local-dev.p12` nunca estuvieron en git.
- **SAST: perfil Maven `security-scan`.** SpotBugs 4.10.5.0 con FindSecBugs 1.14.0 y OWASP Dependency-Check 13.0.0, sin acoplar al ciclo normal. **El primer límite (`maxRank=9`) no detectaba una inyección SQL sembrada** (FindSecBugs la clasifica en rango 12); se subió a 14 con confianza media o alta y se verificó con una prueba de mutación (falla con 2 hallazgos; pasa al retirar la clase).
- **Clasificación de los 23 hallazgos reales.** 5 avisos de posible nulo, corregidos en 3 lugares del código (comprobación de tipo del principal en `ApiExceptionHandler`, `MfaGateFilter` y `UserRateLimitFilter`). 18 excluidos con justificación escrita y limitados a una clase y un patrón: `COMMAND_INJECTION` (6, argumentos de proceso solo del sistema), `UNVALIDATED_REDIRECT` (9, ruta constante), `UNENCRYPTED_SOCKET` (1, sonda TCP sin datos), `ENTITY_LEAK` (2, caché pública de NVD).
- **CodeQL.** El repositorio es público, así que el análisis es posible. No sube a *Code scanning* porque exige `security-events: write`, prohibido por la regla de la fase; guarda un SARIF y falla por severidad de seguridad 7.0 o más. Se mantiene con `continue-on-error` hasta verificar en el primer PR que extrae bien Java 25.
- **DAST (opcional) no incluido.** Un escaneo base de ZAP sin sesión solo recorre el login y el registro; se retoma con el flujo de informes.
- **Dependency-Check** solo se probó hasta su arranque en local (sin `NVD_API_KEY`); su ejecución completa queda para el PR de prueba. En fork PRs se omite con advertencia porque GitHub no entrega secretos.
- **Repositorio renombrado.** Ahora es `github.com/F25DY/TinyHat-` y es público. El remoto local sigue con el nombre anterior (GitHub redirige).
- **`mvnw clean` falla en local** por archivos de `target` abiertos por el IDE u OneDrive. En CI no aplica; el pipeline usa `clean` porque `hexora/target` está versionado.
- **Corrección documental.** En `reports/fase-12.md`, una referencia cruzada apuntaba a §4 en lugar de §5.

## 2026-11-11 — Fase 12 (auditoría y registros) y cimientos de informes

- **Autorización de inicio.** La persona pidió iniciar la Fase 12 tras validar herramientas y sembrar los cimientos de informes. La Fase 11 no tiene un `APROBADA FASE 11` escrito; se trabajó sobre la misma rama por instrucción directa.
- **Respaldo previo.** `C:\Users\Fredy\OneDrive\Documentos\dumps\fase-12\20261111_214942\` (volcado de `hexora_db` en V27, `.env` y árbol de trabajo sin `target`).
- **Migración V28 `report_content_foundation`.** Se aplicó a `hexora_db` (V27 a V28) con Flyway como `admin1`. Solo altera `informe`, que estaba vacía; no crea tablas. El plan reservaba la numeración V18 a V27 y no preveía V28: se usa el siguiente número libre (regla de §2).
- **Informes: cimientos sin generadores.** Snapshot JSON fijo con SHA-256, API, panel del auditor y interfaz `ReportRenderer`. La descarga responde 501 hasta que existan los generadores. Tipos: técnico PDF, ejecutivo PDF (lenguaje formal no técnico), recopilatorio de mitigaciones PDF y matriz de riesgo Excel. Se asumió `.xlsx` en lugar de `.xls`; confirmar con la persona.
- **Solo inserción por permisos de tabla, no por trigger.** MySQL con registro binario exige `SUPER` o `log_bin_trust_function_creators` para crear triggers con una cuenta sin `SUPER`, lo que obligaba a cambiar la configuración del servidor y de todos los contenedores de prueba. Se aplicaron `GRANT` por tabla (`audit_event` solo `SELECT` e `INSERT`) con `hexora/database/grant-runtime-privileges.sql`, que hay que repetir tras migraciones que creen tablas. El plan lo permitía ("aplicar si el motor lo permite en local").
- **Equivalente a ArchUnit.** `AuditAppendOnlyTest` usa reflexión y análisis de fuentes. ArchUnit no se añadió para no depender de que soporte los bytecodes de Java 25 y 27.
- **Eventos nuevos y comandos del propietario.** Además de la lista del plan: `USER_VERIFIED`, `USER_REACTIVATED`, `REPORT_CREATED`, `REPORT_UNPUBLISHED`. Para que `USER_SUSPENDED` y `CAPABILITY_CHANGED` tengan una ruta real de emisión se añadieron los comandos `suspend-usuario`, `reactivate-usuario` y `set-capability` al ejecutor del propietario (mismas reglas que Fase 8: solo con `--tinyhat.ops.enabled=true` y sin servidor web). `ROLE_CHANGED` y la variante detectada de `USER_SUSPENDED` se emiten cuando una sesión viva nota el cambio.
- **`OAUTH_LINKED` reservado.** Está en el catálogo sin emisor, porque OAuth2/OIDC se descartó en Fase 9. Una prueba lo verifica.
- **`THROTTLED` distinto de `RATE_LIMITED`.** El bloqueo temporal de login ahora emite `THROTTLED`; el agotamiento del cubo de fichas sigue siendo `RATE_LIMITED`.
- **Logs.** `logback-spring.xml` con `%mask(%m%n%ex)`, archivo rotativo `logs/tinyhat.log` (ignorado por git) y un logger aparte `tinyhat.notifier.console` que mantiene visibles los enlaces de un solo uso en consola (solo `dev` y `demo`), nunca en el archivo.
- **Docker y cuentas de base de datos.** `docker-compose.yml` usa `FLYWAY_DB_USER` como cuenta de migración (privilegios completos) y crea la cuenta de ejecución con `database/init/01-runtime-user.sh`. El contenedor y volumen existentes no se modificaron; el script de inicio se verificó con un contenedor desechable.
- **Configuración de VS Code del equipo.** Las tareas de `.vscode/tasks.json` apuntan a rutas de otro equipo y no se modificaron.
- **Datos ficticios.** Las validaciones crearon auditores, proyecto, área, hallazgo y borradores de informe ficticios en la base compartida, y una fila `GRANT_CHECK` en `audit_event` de la comprobación de permisos.

## 2026-11-11 — Cierre técnico de Fase 11 (sin PDF)

- Antes de tocar nada se creó un respaldo fuera del repositorio (`C:\Users\Fredy\OneDrive\Documentos\dumps\fase-11\20261111_131323\`): archivo completo del árbol de trabajo con `.git`, volcado de `hexora_db` en V27, copia del `.env` y `README-ROLLBACK.md` con los pasos de restauración. Sumas SHA-256 en `SHA256SUMS.txt`.
- **Perfiles perdidos.** Los archivos `application-dev/demo/prod.properties` creados en Fase 1 (cableado `server.ssl.*` ← `TINYHAT_SSL_*`) no estaban en ninguna rama: fueron borrados en el commit huérfano `fead790` ("Agent host session … turn 6 start"). Se recrearon. `prod` fija valores estrictos sin variables de relajación; `dev`/`demo` permiten HTTPS local y añaden `https://127.0.0.1:8443` como segundo origen WebSocket.
- **Inventario de Fase 0 recuperado.** `hexora/docs/security/00-inventario.md` y `reports/fase-00.md` existían en la misma historia huérfana (`git show fead790^:…`) y se restauraron sin cambios; el pendiente documental de Fase 0 queda cerrado.
- **CSP configurable.** `tinyhat.security.csp.report-only` no se consultaba al emitir la cabecera. Ahora `SecurityConfiguration` construye la política con `ContentSecurityPolicy.directives(...)`: `connect-src` se deriva de `tinyhat.security.ws.allowed-origins` (https→wss) y el modo report-only obedece la propiedad. Se eliminó el origen `ws://127.0.0.1:8080` fijo en código.
- **Corrección de 404 con ámbito.** Con la app real, `GET /proyectos/<uuid inexistente>` devolvía 500 porque `ApiExceptionHandler` (`Exception.class`) se evaluaba antes que `ScopedNotFoundExceptionHandler` (ambos sin orden explícito). Se fijó `@Order(HIGHEST_PRECEDENCE)` en el manejador de ámbito y `HIGHEST_PRECEDENCE + 10` en el de red; prueba nueva en `PhaseThreeSecurityIntegrationTest`.
- **WebSocket.** El interceptor de handshake responde 403 al rechazar (antes 200 vacío). Prueba añadida.
- **Regresiones de la sesión anterior detectadas por la suite completa.** (a) `ApiExceptionHandler` (`Exception.class`) convertía `HttpRequestMethodNotSupportedException` en 500: se añadió un manejador 405 con cabecera `Allow` (y 404 para `NoResourceFoundException`); el filtro de diagnóstico 405 del módulo de red vuelve a funcionar. (b) `MonitoredPortRequest` exigía `port` **y** `enabled` con `@NotNull` aunque POST `/ports` solo envía `port` y PUT `/ports/{port}` solo `enabled`; el cuerpo fallaba antes de buscar el host y un host ajeno respondía 400 en vez de 404. Se retiró `@NotNull`; el servicio sigue validando la presencia del campo que usa cada operación y el rango 1–65535 se mantiene por anotación.
- **Aislamiento de pruebas respecto al `.env`.** `spring.config.import=optional:file:.env` también alimenta los contextos de prueba; al activar HTTPS en el `.env` local, la prueba de Fase 5 con servidor embebido recibió 400 (TLS requerido). Surefire fija `TINYHAT_SSL_ENABLED=false` y la prueba declara `server.ssl.enabled=false`.
- **Pruebas desactualizadas de Fase 3.** `secondLoginReplacesThePreviousSession` y `unlistedRoutesAreDenied…` fallaban por cambios de Fase 10 (`/api/network/status` exige `projectPublicId`; `SessionActivityFilter` expira sesiones simuladas sin marcas de tiempo). Se actualizaron a `/api/network/projects` y a una sesión real sin cambiar el criterio verificado.
- **Privilegios de BD.** `spring.datasource.url` ya no lleva `createDatabaseIfNotExist`; solo la URL de Flyway. En el Docker local se creó `hexora_app` (SELECT/INSERT/UPDATE/DELETE) como usuario de ejecución y `admin1` quedó como usuario de Flyway. Se comprobó que CREATE/ALTER/DROP fallan con ERROR 1142 y que la app arranca con `ddl-auto=validate`. Contrato y script en `docs/security/db-privilegios.md`.
- **Validación con la aplicación real** (HTTPS en `https://localhost:8443`, certificado local): 29 comprobaciones automatizadas (`hexora/src/test/js/fase11-validacion-https.mjs`) que cubren cabeceras, CSP en modo de aplicación sin recursos inline ni externos en las páginas principales, registro→verificación→login→TOTP, 404 con ámbito y handshake WebSocket real (101 con origen permitido y sesión MFA; 403 con origen ajeno o sin `Origin`; redirección a login sin sesión). Las ejecuciones crearon dos auditores ficticios `auditor.validacion.fase11.<ts>@tinyhat.local` en la BD compartida (datos ficticios; el informe indica cómo limpiarlos).
- **Reintentos HTTP salientes: decisión cerrada.** No se añaden reintentos automáticos hacia NVD ni MyMemory: la caché local es la primera defensa, el limitador NVD informa `Retry-After` y un fallo transitorio degrada a resultados locales. Repetir peticiones multiplicaría el consumo de cuota sin beneficio de seguridad.
- **PDF sigue fuera.** F11-CA4 y F11-CA8 permanecen parciales hasta que la persona defina el contenido del informe publicado. Fase 11 se entrega para revisión sin esa parte; no se inicia Fase 12.
- Cambios locales no versionados: `hexora/.env` (APP_PORT 3000→8443, HTTPS, `hexora_app`, `TINYHAT_MFA_KEY`, `PUBLIC_BASE_URL`), `hexora/local-dev.p12`, `hexora/localhost.crt`.

## 2026-11-10 — Avance de Fase 11

- Se endurecieron las búsquedas CVE y las rutas de paginación del módulo de red, se normalizaron errores API a códigos genéricos con `correlationId` y se corrigió el mapeo de denegaciones de autorización a 403.
- NVD y MyMemory usan clientes HTTPS con hosts fijos, timeouts, redirecciones desactivadas y límites de respuesta. MyMemory acepta únicamente la descripción de un CVE validado.
- Se retiraron estilos inline del buscador CVE y del informe web de red. Se añadieron pruebas para XSS en hostname, descripciones CVE, nombres de proyecto/organización e informe generado en navegador.
- El perfil `prod` requiere credenciales Flyway propias y falla al iniciar si Flyway intenta usar el mismo usuario que la conexión runtime.
- Pruebas focalizadas de salida externa, validación, CSP, límite de paginación y plantillas pasan; no se modificó la base compartida.
- Fase 11 continúa abierta: no hay motor/endpoint PDF ni contenido de informe persistido; faltan validación visual manual de CSP y verificación operativa de grants para runtime/Flyway. No se inicia Fase 12. Ver [reporte de Fase 11](./reports/fase-11.md).

## 2026-11-10 — Despliegue de V27 en la base Docker

- Se creó y verificó un respaldo completo comprimido de `hexora_db` antes de modificarla. El archivo `hexora_db_pre_v27_2026-11-10.sql.gz` quedó fuera del repositorio; SHA-256: `0899b4c462e537985225ce554edb52ae1f31d86786f92bda9851eef792e22f60`.
- Flyway validó las migraciones y aplicó V25, V26 y V27 exitosamente en el contenedor `hexora-mysql` (MySQL 8.0.46). El historial confirma las tres versiones y la base quedó en V27.
- Se verificaron las tablas MFA/tokens, las tablas de alcance y bloqueo, las columnas de proyecto y el índice único de dispositivos. La cuenta ya tenía `monitoreo_continuo` habilitado; la advertencia de duplicado fue ignorada por `INSERT IGNORE` y el valor existente se preservó.
- La base migrada no implica que la aplicación tenga el código nuevo: el proceso web continúa ejecutándose en el host y no fue reiniciado. Ver [reporte de Fase 10](./reports/fase-10.md).

## 2026-11-10 — Cierre de implementación técnica de Fase 10

- Se extrajo la sonda de puertos a `NetworkProbe`/`SocketNetworkProbe` y `NetworkPortScanExecutor`. El ejecutor limita cada operación a 32 puertos, aplica timeout configurable (1–30 s, predeterminado 30 s) y traduce saturación/timeout a rechazo controlado. Las pruebas sustituyen la sonda, sin abrir sockets.
- Se corrigió la selección del constructor Spring del ejecutor, detectada por la prueba web con Testcontainers. También se endureció la liberación de locks: solo se considera liberado cuando `RELEASE_LOCK` devuelve 1; los errores se propagan y se sigue liberando el lock global.
- Se completó la auditoría de éxitos de escaneo programado y de retención cuando elimina registros, junto con los eventos de fallo ya existentes. El scheduler requiere capacidad de red y monitorización continua, y solo procesa proyectos habilitados de forma explícita; V27 conserva las preferencias de capacidad ya existentes y da estado inicial a las cuentas que carecen de registro.
- La terminación de sesiones se propaga al WebSocket handler para expiración, suspensión, reinicio MFA y recuperación de contraseña. El intento de handshake no autenticado se rechaza en el flujo HTTP/WebSocket; las pruebas del interceptor cubren origen, sesión, MFA, usuario activo y capacidad.
- Antes de cada publicación WebSocket se revalida el rol/estado actual de usuario y cuenta; un cambio de rol invalida la capacidad de publicar y cierra la conexión.
- Se cotejaron los criterios F10-CA1–F10-CA11 del documento rector proporcionado y se añadió una matriz de evidencia al informe. Se eliminaron pruebas que ejecutaban `ping` real contra loopback; las sondas de prueba usan dobles.
- Regresión focalizada: **135 pruebas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. Las 25 pruebas web ejecutaron V27 contra MySQL desechable de Testcontainers; Docker estaba disponible. `node --check` pasó en ambos scripts JS, `node --test src/test/js/network-monitoring-security.test.js` pasó (1/1) y `git diff --check` pasó. Esta ejecución de pruebas no modificó la BD compartida.
- Fase 10 tiene la implementación técnica completa y evidencia para los once criterios. La aplicación de V27 a la BD Docker local quedó registrada en la entrada posterior de despliegue. Ver [reporte de Fase 10](./reports/fase-10.md).

## 2026-11-09 — Inicio de Fase 10

- Se asignó V27 a la Fase 10 para rangos de red autorizados por proyecto, bloqueos de escaneo y la asociación opcional de hosts existentes a un proyecto. La migración solo se validó en una base desechable mediante las pruebas web de red; no se aplicó a la BD compartida.
- Se inició la protección fail-closed de escaneos y descubrimientos: destino IPv4, interfaz y rango vigente deben coincidir con el proyecto; se exige la capacidad `modulo_red` y se agregaron límites por prefijo y bloqueos/cooldown persistentes. Se endureció el handshake WebSocket para requerir sesión autenticada, MFA y capacidad de red, y se desactivó el descubrimiento automático que no puede demostrar un proyecto autorizado.
- Se corrigió el orden de validación de hosts: un host fuera de la allowlist conserva el resultado de no encontrado antes de que se compruebe el vínculo a proyecto. Las pruebas focalizadas de red pasan: 39 pruebas, 0 fallos, 0 errores.
- Ver [reporte de avance de Fase 10](./reports/fase-10.md). No ejecutar V27 contra la BD compartida sin respaldo, aprobación y ventana de migración.

## 2026-11-09 — Continuación de Fase 10: aislamiento por proyecto

- Se completaron la selección del proyecto visible, declaración/listado de rangos y el envío del proyecto en las operaciones principales de monitorización. Las lecturas de inventario, gateway, snapshots y eventos de detección consultan el proyecto asociado; los eventos WebSocket revalidan permisos al entregar cada actualización.
- V27 también separa inventario detectado e historial mediante `proyecto_id`. Los datos históricos ambiguos permanecen sin proyecto y no se exponen por coincidencia de dirección/subred. La migración y el nuevo aislamiento se verificaron en la base MySQL desechable; no se ejecutó en la base compartida.
- El estado local del navegador se separa por proyecto y la interfaz limpia los datos visibles al cambiar de selección. Se agregaron pruebas contra la filtración entre proyectos que autorizan la misma subred.
- El historial ahora pagina hasta reunir la cantidad solicitada de snapshots todavía autorizados. WebSocket revalida también la capacidad de red y cierra conexiones si se revoca durante la sesión.
- Las pruebas ejercitan concurrencia/expiración del cooldown con MySQL desechable, rechazos previos al escáner, controles WebSocket de MFA/origen/tamaño/mensajes/terminación/revocación, y rechazos cross-project en lecturas, mutaciones, borrados y limpieza.
- Las rutas HTTP de escaneo y descubrimiento registran resultados de éxito, rechazo por alcance/cooldown y errores de descubrimiento. Errores de escaneos y tareas de retención programadas generan eventos de auditoría por cuenta.
- Regresión focalizada: **101 pruebas, 0 fallos, 0 errores y 1 omitida; BUILD SUCCESS**. `node --check` pasó en ambos scripts de monitorización.
- Fase 10 **sigue en curso y no está aprobada**. Pendientes: cobertura extendida de locks y handshake real, instrumentación del escáner y auditoría de ejecuciones exitosas programadas. El scheduler no debe descubrir dispositivos automáticamente hasta completar el contexto de proyecto y sus controles.

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
