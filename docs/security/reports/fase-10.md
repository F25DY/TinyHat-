# Fase 10 — Rangos de red autorizados y controles de escaneo

## 1. Estado

**Implementación técnica completa; pendiente de aprobación formal.** Se asignó V27 a la Fase 10. Los criterios F10-CA1–F10-CA11 se cotejaron con el documento rector proporcionado (`TINYHAT_SECURITY_IMPLEMENTATION (1).md`, sección Fase 10); el archivo fuente permanece fuera del repositorio.

V27 se aplicó y verificó en la base `hexora_db` del contenedor Docker local `hexora-mysql`. Se generó un respaldo previo y Flyway confirmó la transición V24→V27. Los registros de inventario y detección anteriores a V27 quedan sin proyecto y no se muestran dentro de proyectos; deberán volver a detectarse o reasignarse mediante un proceso explícito antes de depender de ellos.

## 2. Implementación realizada

- V27 incorpora rangos IPv4 autorizados y bloqueos/cooldown de escaneo por proyecto. También asocia hosts a proyectos y añade `proyecto_id` al inventario detectado y al historial de detección, con claves foráneas compuestas por cuenta y proyecto.
- El descubrimiento y las lecturas activas ahora buscan el inventario, gateway, snapshots y eventos de detección dentro del proyecto seleccionado, además de comprobar que las direcciones/subredes sigan dentro de rangos vigentes. Los snapshots e inventarios antiguos sin proyecto no se asignan implícitamente.
- Se implementó la selección de proyectos visibles, la declaración/consulta de rangos, y el envío del proyecto en escaneos, descubrimientos, registro de dispositivos e historial. Las lecturas y mutaciones principales de hosts, puertos, escaneos e historial requieren proyecto; los hosts no se pueden reasignar entre proyectos.
- El estado de la interfaz (alertas, configuración, etiquetas, inventario previo y recordatorios) se guarda por proyecto. Al cambiar de proyecto se limpian inmediatamente las vistas y métricas previas.
- WebSocket requiere sesión autenticada, usuario activo, MFA completado, rol auditor y capacidad de red; rechaza mensajes del cliente y no admite orígenes wildcard. Las publicaciones se filtran por proyecto y revalidan el permiso del usuario al momento de entrega para no mantener accesos revocados en una conexión abierta.
- Las conexiones WebSocket se cierran si se revoca la capacidad de red mientras están abiertas. El historial continúa paginando por snapshots del proyecto hasta reunir el límite solicitado que aún pertenece a rangos autorizados.
- Las pruebas ejercitan concurrencia y expiración del cooldown con MySQL desechable, así como MFA incompleto, origen wildcard, rechazo de mensajes de cliente, límite de tamaño, cierre por terminación de sesión y revocación del acceso a un proyecto. También verifican rechazos cross-project de lectura de historial, mutaciones/borrados de hosts y puertos, y limpieza de eventos.
- Las rutas HTTP de escaneo y descubrimiento registran inicio, finalización, rechazo por alcance/cooldown y errores de descubrimiento, sin convertir esos fallos en respuestas exitosas.
- Los rechazos de alcance de la interfaz y del destino de escaneo se prueban antes de leer vecinos, abrir sockets o adquirir el cooldown. Los errores de escaneos y mantenimiento programados se guardan en la auditoría de cuenta con tipo de fallo y sin incluir direcciones de destino.
- El descubrimiento automático del scheduler continúa desactivado porque aún no dispone de un proyecto autorizado explícito. Las operaciones activas mantienen las guardas de capacidad, rango, dirección/interfaz, concurrencia y cooldown.
- Se añadieron pruebas de aislamiento que verifican que inventario, historial y eventos de otro proyecto no se filtran aunque ambos proyectos autoricen la misma subred.
- V27 deja `monitoreo_continuo_habilitado` desactivado por proyecto y asegura que las cuentas que aún no tengan la capacidad `monitoreo_continuo` reciban el valor inicial sin sobrescribir preferencias ya existentes. El scheduler exige ambas capacidades y solo consulta proyectos que habilitaron explícitamente la monitorización.
- Los cambios de estado de sesión por suspensión de cuenta, expiración, reinicio de MFA y recuperación de contraseña publican terminación para cada sesión expirada, permitiendo cerrar de inmediato sus WebSockets. Se verificó la propagación del evento y el cierre asociado.
- Antes de cada publicación, el permiso de red reconsulta el estado y rol actuales del usuario y de la cuenta; si el rol cambia, el guard rechaza la sesión y el handler cierra el WebSocket sin difundir el evento.
- La solicitud HTTP de upgrade a `/ws/network` sin autenticación se rechaza antes de aceptar la conexión; las pruebas del interceptor también cubren origen no permitido, sesión ausente, MFA incompleto, usuario inactivo y capacidad ausente.
- La interfaz escapa los datos del inventario descubierto; una prueba ejecuta `renderDetectedInventory` con un hostname `<script>` y verifica que el HTML generado contenga `&lt;script&gt;`.
- Se eliminaron pruebas que ejecutaban `ping` real contra loopback. Las pruebas del escáner usan dobles inyectados; no se sondean hosts de red reales.

## 3. Validación ejecutada

Regresión focalizada actualizada de Fase 10:

```powershell
.\mvnw.cmd "-Dtest=NetworkMonitoringWebTest,NetworkMonitoringServiceTest,NetworkMonitoringSchedulerTest,NetworkMonitorWebSocketHandlerTest,NetworkWebSocketHandshakeInterceptorTest,NetworkApiExceptionHandlerTest,Ipv4CidrTest,NetworkScopeGuardTest,NetworkCapabilityGuardTest,NetworkScheduledTaskLockTest,NetworkPortScanExecutorTest,NetworkScanLockServiceTest,NmapHostDiscoveryTest,NetworkReachabilityProbeTest,LocalNetworkDiscoveryServiceTest,LocalNeighborPresenceTrackerTest,SessionActivityFilterTest,SessionTerminationEventPublisherTest" test
```

Resultado de la regresión de implementación: **135 pruebas, 0 fallos, 0 errores y 0 omitidas; BUILD SUCCESS**. Docker/Testcontainers inició la base MySQL desechable y las 25 pruebas web pasaron, incluida la validación de V27. Esta ejecución de pruebas no modificó la BD compartida.

### Despliegue de V27 en Docker (2026-11-10)

- Antes de migrar se creó fuera del repositorio el respaldo comprimido `hexora_db_pre_v27_2026-11-10.sql.gz` (15.948 bytes; SHA-256 `0899b4c462e537985225ce554edb52ae1f31d86786f92bda9851eef792e22f60`). Se verificó que contiene el esquema y datos de la base.
- Flyway validó las 28 migraciones y aplicó exitosamente V25, V26 y V27 en secuencia sobre MySQL 8.0.46. El historial registra las tres como exitosas y la versión actual quedó en V27.
- Se comprobaron las cinco tablas nuevas, `auditoria.monitoreo_continuo_habilitado`, `proyecto_id` en las tres tablas de red y el índice único `(cuenta_id, proyecto_id, address)`. Se conservaron una cuenta y dos proyectos existentes.
- V27 emitió una advertencia de clave duplicada al sembrar `monitoreo_continuo`: la cuenta ya tenía esa capacidad. `INSERT IGNORE` preservó su valor habilitado (`1`) y Flyway completó la migración sin fallos.
- Solo se modificó la base del contenedor `hexora-mysql`; no se reiniciaron contenedores. La aplicación sigue ejecutándose en el host y no se reinició ni actualizó como parte de esta operación de base de datos. Para exponer el código nuevo de Fase 10 en la aplicación, aún se requiere desplegar/reiniciar la aplicación con la versión actualizada.

La suite comprueba la ejecución simulada de sondas, el límite de 32 puertos, timeout y saturación del ejecutor; también verifica locks, propagación del fallo al liberar y liberación del lock global restante. WebSocket cubre usuario deshabilitado, falta de capacidad, origen exacto configurado, rechazo wildcard, terminación de sesión, límite de mensajes y revocación. El scheduler cubre auditoría de escaneo exitoso/fallido y retención con borrados. El handshake no autenticado se rechaza en el flujo HTTP/WebSocket sin abrir una conexión de escaneo.

`node --check` pasó para `network-monitoring.js` y `network-monitoring-history.js`; `node --test src/test/js/network-monitoring-security.test.js` pasó (1/1). `git diff --check` pasó en documentación y fuentes (`docs/security` y `hexora/src`).

## 4. Evidencia por criterio de aceptación

La matriz coteja el texto del documento rector proporcionado con las pruebas ejecutadas:

| ID | Verificación | Prueba/comando | Resultado |
|---|---|---|---|
| F10-CA1 | Rechazo sin rango vigente, auditoría `SCAN_REJECTED` y sin iniciar escaneo | `NetworkMonitoringWebTest.rejectsAProjectScanWithoutAnAuthorizedRangeAndAuditsTheRejectedTarget`; `NetworkScopeGuardTest.rejectsMissingOrExpiredAuthorizationBeforeAnyDiscoveryOrScannerCanRun` | Cumple |
| F10-CA2 | Interfaz activa fuera del rango del proyecto se rechaza antes del descubrimiento | `NetworkScopeGuardTest.rejectsAnActiveInterfaceOutsideTheProjectRange`; `NetworkMonitoringServiceTest.rejectsDiscoveryBeforeReadingTheNetworkWhenProjectInterfaceIsOutOfScope` | Cumple |
| F10-CA3 | Rechazo de rango público y prefijo más amplio que /24 por defecto | `NetworkScopeGuardTest.rejectsPublicAndTooBroadAuthorizedRangesByDefault` | Cumple |
| F10-CA4 | Rechazo de metacaracteres, hostname y puertos 0/70000 antes de sondas/procesos | `NetworkScopeGuardTest.rejectsHostnamesAndShellMetacharactersWithoutResolvingThem`; `NetworkMonitoringServiceTest.rejectsPortsOutsideTcpPortRange`; `NetworkPortScanExecutorTest.rejectsInvalidOrUnboundedPortListsBeforeSchedulingAnyProbe` | Cumple |
| F10-CA5 | Solo un intento concurrente obtiene cooldown; lock ocupado no inicia sonda y se traduce a HTTP 429 | `NetworkMonitoringWebTest.allowsOnlyOneConcurrentCooldownGrantAndReopensAfterExpiry`; `NetworkScanLockServiceTest.releasesGlobalLockWhenProjectLockIsBusy`; `NetworkMonitoringServiceTest.translatesAnOccupiedProjectLockIntoARateLimitWithoutStartingAProbe`; `NetworkApiExceptionHandlerTest.respondsWithTooManyRequestsWhenAProjectScanLockIsBusy` | Cumple |
| F10-CA6 | El hostname `<script>` se renderiza como texto escapado | `node --test src/test/js/network-monitoring-security.test.js` | Cumple |
| F10-CA7 | Origen no permitido, sesión ausente o MFA incompleto impiden el handshake | `NetworkWebSocketHandshakeInterceptorTest.rejectsUnlistedOrMissingOriginsBeforeCheckingAuthentication`; `NetworkWebSocketHandshakeInterceptorTest.rejectsMissingSessionAndPreMfaAuthenticationDuringTheHandshake`; `NetworkMonitoringWebTest.rejectsAnUnauthenticatedWebSocketHandshake` | Cumple |
| F10-CA8 | Eventos de una cuenta/proyecto no se entregan a otra sesión sin autorización; el rol actual se revalida | `NetworkMonitorWebSocketHandlerTest.publishesOnlyToSessionsInTheActiveTenant`; `NetworkCapabilityGuardTest.rejectsSessionsAfterTheUsersRoleChanges` | Cumple |
| F10-CA9 | Terminación de sesión cierra WebSocket en menos de cinco segundos | `NetworkMonitorWebSocketHandlerTest.closesWebSocketSessionsWhenTheirHttpSessionIsTerminated`; `SessionTerminationEventPublisherTest.expiresTheSessionAndPublishesItsTermination` | Cumple |
| F10-CA10 | `modulo_red=false` produce 403 sin incrementar escaneos | `NetworkMonitoringWebTest.rejectsNetworkApiCallsWhenTheNetworkCapabilityIsDisabled` | Cumple |
| F10-CA11 | Las sondas de red de las pruebas se sustituyen por dobles; no se ejecuta ping ni escaneo real | `NetworkPortScanExecutorTest`; `NetworkMonitoringServiceTest`; regresión focalizada | Cumple |

V27 se mantiene fuera de la BD compartida hasta contar con respaldo, aprobación y ventana de migración. La fase puede someterse a aprobación técnica; la aplicación de la migración en producción sigue siendo un despliegue pendiente autorizado por separado.
