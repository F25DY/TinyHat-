# Fase 10 — Rangos de red autorizados y controles de escaneo

## 1. Estado

**EN CURSO; no aprobada.** Se asignó V27 a la Fase 10. Se completó una parte importante del aislamiento por proyecto, pero aún no se cierran todos los criterios F10-CA1–F10-CA11.

V27 se validó únicamente en la base MySQL desechable de pruebas. **No se aplicó a la BD compartida.** Los registros de inventario y detección anteriores a V27 quedan sin proyecto y no se muestran dentro de proyectos; deberán volver a detectarse o reasignarse mediante un proceso explícito antes de depender de ellos.

## 2. Implementación realizada

- V27 incorpora rangos IPv4 autorizados y bloqueos/cooldown de escaneo por proyecto. También asocia hosts a proyectos y añade `proyecto_id` al inventario detectado y al historial de detección, con claves foráneas compuestas por cuenta y proyecto.
- El descubrimiento y las lecturas activas ahora buscan el inventario, gateway, snapshots y eventos de detección dentro del proyecto seleccionado, además de comprobar que las direcciones/subredes sigan dentro de rangos vigentes. Los snapshots e inventarios antiguos sin proyecto no se asignan implícitamente.
- Se implementó la selección de proyectos visibles, la declaración/consulta de rangos, y el envío del proyecto en escaneos, descubrimientos, registro de dispositivos e historial. Las lecturas y mutaciones principales de hosts, puertos, escaneos e historial requieren proyecto; los hosts no se pueden reasignar entre proyectos.
- El estado de la interfaz (alertas, configuración, etiquetas, inventario previo y recordatorios) se guarda por proyecto. Al cambiar de proyecto se limpian inmediatamente las vistas y métricas previas.
- WebSocket requiere sesión autenticada, usuario activo, MFA completado, rol auditor y capacidad de red; rechaza mensajes del cliente y no admite orígenes wildcard. Las publicaciones se filtran por proyecto y revalidan el permiso del usuario al momento de entrega para no mantener accesos revocados en una conexión abierta.
- Las pruebas ejercitan concurrencia y expiración del cooldown con MySQL desechable, así como MFA incompleto, origen wildcard, rechazo de mensajes de cliente, límite de tamaño, cierre por terminación de sesión y revocación del acceso a un proyecto. También verifican rechazos cross-project de lectura de historial, mutaciones/borrados de hosts y puertos, y limpieza de eventos.
- Las rutas HTTP de escaneo y descubrimiento registran inicio, finalización, rechazo por alcance/cooldown y errores de descubrimiento, sin convertir esos fallos en respuestas exitosas.
- El descubrimiento automático del scheduler continúa desactivado porque aún no dispone de un proyecto autorizado explícito. Las operaciones activas mantienen las guardas de capacidad, rango, dirección/interfaz, concurrencia y cooldown.
- Se añadieron pruebas de aislamiento que verifican que inventario, historial y eventos de otro proyecto no se filtran aunque ambos proyectos autoricen la misma subred.

## 3. Validación ejecutada

Pruebas focalizadas:

```powershell
.\mvnw.cmd "-Dtest=NetworkMonitoringWebTest,NetworkMonitoringServiceTest,NetworkMonitoringSchedulerTest,NetworkMonitorWebSocketHandlerTest,Ipv4CidrTest,NetworkScopeGuardTest,NetworkCapabilityGuardTest,NetworkScheduledTaskLockTest,NmapHostDiscoveryTest,NetworkReachabilityProbeTest,LocalNetworkDiscoveryServiceTest,LocalNeighborPresenceTrackerTest" test
```

Resultado: **96 pruebas, 0 fallos, 0 errores y 1 omitida; BUILD SUCCESS**. Las pruebas web validaron la migración en una base desechable. `node --check` pasó para ambos scripts de monitorización.

## 4. Pendiente antes de solicitar aprobación

- Ampliar las pruebas del cooldown y de locks globales/proyecto, incluyendo fallos de adquisición/liberación y comportamiento en todas las rutas activas.
- Completar la cobertura de handshake WebSocket para usuario deshabilitado, falta de capacidad, orígenes permitidos en el handshake real y cierre de sesión dentro del plazo requerido.
- Instrumentar el escáner para demostrar rechazos sin tráfico real y completar pruebas de timeout, límites de recursos y destinos IPv4/CIDR adversos.
- Completar auditoría de ejecuciones y errores del scheduler/retención, y revisar las rutas de administración restantes con pruebas cross-project.
- Revisar la paginación del historial de detección: actualmente se limita la lectura a una ventana reciente antes de filtrar rangos expirados, por lo que puede retornar menos resultados que el límite solicitado.
- Ejecutar la regresión aplicable y validar V27 exclusivamente en una base desechable antes de cualquier propuesta de despliegue.

No declarar la fase completa hasta cerrar F10-CA1–F10-CA11 con evidencia y obtener aprobación del usuario.
