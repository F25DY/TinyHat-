# Fase 14 — Verificación integral y cierre

## 1. Estado

**Implementación y documentación completas. Los tres hallazgos de la primera entrega (H-1, G-1 y G-2) quedaron corregidos por instrucción tuya y verificados.** Queda F14-CA4 (recorrer el runbook), que solo puedes hacer tú. No se cerró ningún criterio sin prueba.

Rama: `seguridad/fase-14`, creada desde `seguridad/fase-13` (`be25481`). `main` aún no contiene la Fase 13. No se usó `prueba/ci-secreto`. Los archivos de esta fase siguen **sin commit**, como en las fases anteriores.

Aprobación de la Fase 13: tomé "Fase 13 aprobada" como `APROBADA FASE 13`. Está anotado en el log.

## 2. Qué se entregó

| Archivo | Propósito |
|---|---|
| `docs/security/trazabilidad.md` | Decisión → fase → criterio → prueba, con estado real y resumen de los 99 criterios de F0 a F13 |
| `docs/security/inventario-final.md` | Repetición de la Fase 0: 90 combinaciones método + ruta, comparación con el inventario inicial |
| `docs/security/runbook-demo.md` | Guion de demostración en 6 escenas, con preparación, solución de fallos y limpieza |
| `docs/security/limitaciones.md` | Lo que la v1 no cubre y por qué |
| `docs/security/IMPLEMENTATION_LOG.md` | Entrada de la Fase 14 |
| `hexora/src/main/resources/application.properties` y `TinyHatSecurityProperties.java` | **Único cambio de código de producción:** 3 rutas añadidas a `poll-paths` (H-1) |
| `FinalEndpointInventoryIntegrationTest` | 3 pruebas: inventario de endpoints, rutas reservadas y configuración de loopback |
| `SessionPollPathsConsistencyTest` | 2 pruebas: las consultas periódicas de la interfaz están en `poll-paths` |
| `CrossAccountIsolationIntegrationTest` | 3 pruebas: matriz de aislamiento entre cuentas (G-1, G-2) |
| `RateLimitCategoryTest` | Una aserción actualizada: esas rutas ahora son `POLL`, no `API_GENERAL` (consecuencia de H-1) |

Durante la fase no hubo migraciones (la base seguía en V28). **Después de la entrega, a petición de la persona, se añadió V29** (`activate_pending_registered_users`: activa dos usuarios de prueba que quedaron en `PENDIENTE_VERIFICACION` por falta de SMTP); la base está ahora en V29. Detalle y riesgo en `IMPLEMENTATION_LOG.md`, sección "Posterior a la entrega". Esta tabla de la entrega no la incluye porque la suite de 256 pruebas se ejecutó antes.

## 3. Criterios de aceptación

| ID | Cómo se verifica | Comando o prueba | Resultado |
|---|---|---|---|
| F14-CA1 | `mvn -B verify` verde | `cd hexora; .\mvnw.cmd -B verify` | **PASA**: 256 pruebas, 0 fallos (248 anteriores + 8 nuevas), ver §4 |
| F14-CA2 | Cada decisión del §1 tiene al menos un criterio y una prueba en la tabla | `docs/security/trazabilidad.md` §1 | **PASA**: las 10 decisiones tienen criterio y prueba. Quedan dos salvedades que no son de esta fase: la decisión 5 incluye OAuth **NO IMPLEMENTADO** (decisión tuya) y F11-CA4/CA8 son parciales por el PDF |
| F14-CA3 | El nuevo inventario muestra cero endpoints sin protección explícita | `FinalEndpointInventoryIntegrationTest.everyRouteIsDeclaredPublicOrDeniedToAnonymousUsers` y `.unmappedFrameworkAndReservedRoutesAreDeniedToAnonymousUsers` | **PASA**: 90 combinaciones; 73 rechazan sin sesión (39 API con 401, 34 web con redirección a `/login`) y 17 son públicas declaradas en una lista cerrada. Ver `inventario-final.md` |
| F14-CA4 | La persona ejecuta el runbook de principio a fin sin pasos no documentados | `docs/security/runbook-demo.md` | **PENDIENTE de la persona.** Yo no lo ejecuté: no puedo usar tu autenticador ni un navegador |

## 4. Validación ejecutada

```powershell
cd hexora
.\mvnw.cmd -B verify
```

| Momento | Pruebas | Resultado |
|---|---|---|
| Línea base (Fase 13, sin cambios míos) | 248 | 0 fallos; `BUILD SUCCESS` |
| Con el inventario de endpoints | 251 | 0 fallos; `BUILD SUCCESS` en 4 min 23 s |
| Con `poll-paths`, consistencia y matriz entre cuentas (primera ejecución) | 256 | **1 fallo**: `RateLimitCategoryTest` afirmaba que `/api/network/status` era `API_GENERAL`; ahora es `POLL` por diseño de H-1 |
| Tras actualizar esa aserción | 256 | **0 fallos, 0 errores, 0 omitidas; `BUILD SUCCESS` en 4 min 4 s** |

El fallo era una consecuencia real del cambio de H-1 y no una prueba floja: la aserción documentaba la clasificación anterior. La actualicé a `POLL` y añadí que `/api/network/scan` y `/api/network/detected-devices` siguen siendo `API_GENERAL`.

Una primera versión del inventario falló con 429: el limitador por IP respondía antes que la autorización porque todas las peticiones salían de la misma IP simulada. No relajé ningún límite: la prueba usa una IP de origen distinta por petición.

### Pruebas de la Fase 4 y aislamiento activo

Ejecutadas dentro de la misma suite (todas en verde; ver las cifras de §4):

| Prueba | Qué demuestra |
|---|---|
| `PhaseFourTenantIsolationIntegrationTest` (3) | Filtro de Hibernate por cuenta: lecturas y escrituras solo del tenant activo; borrar historial no toca otra cuenta; cada auditor ve solo sus proyectos asignados y cada cliente solo los de su organización |
| `NetworkMonitorWebSocketHandlerTest.publishesOnlyToSessionsInTheActiveTenant` | El WebSocket no entrega eventos a otra cuenta |
| `NetworkMonitoringSchedulerTest` (8) | El programador procesa cuentas de forma explícita y respeta capacidades |
| `NetworkMonitoringWebTest` (27) | Aislamiento por proyecto en el módulo de red |
| `ReportFoundationIntegrationTest` (10) | Informes: otra cuenta, auditor no asignado y cliente de otra organización reciben 404 |
| `PhaseTwoMigrationIntegrationTest.rejectsNetworkScanReferencingHostFromAnotherAccount` | La base de datos rechaza enlazar filas de cuentas distintas |
| **`CrossAccountIsolationIntegrationTest` (3, nueva)** | **La matriz que pedía el plan:** 95 peticiones con identificadores ajenos → todas 404, ninguna fila cambia |

**El aislamiento está activo.** Lo comprobé además con una mutación: quité temporalmente de `AuditoriaRepository.findVisibleByPublicId` la exigencia de que el auditor esté asignado al proyecto. La matriz falló de inmediato (`GET /proyectos/{id}` → 200, `/editar` → 200, `/actualizar` y `/eliminar` → 302 sobre un proyecto no asignado). Restauré el archivo con `git checkout` y confirmé que `git status` no lo muestra. Una prueba que no se puede romper no prueba nada; esta sí se rompe.

## 5. Hallazgos (todos cerrados)

### H-1 — Los sondeos de la pantalla de red mantenían viva la sesión → **corregido**

`SessionActivityFilter` solo ignora las rutas de `tinyhat.security.session.poll-paths` (`/api/network/events/**`, `/api/poll/**`, `/ws/**`). La pantalla `/network-monitoring` consulta cada 30 s `/api/network/status`, `/api/network/history` y `/api/network/discoveries/history`, que no estaban en la lista. Con la pantalla abierta la sesión solo expiraba por el tope de 120 minutos.

**Qué hice (opción A):** añadí esas tres rutas a `application.properties` y al valor por defecto de `TinyHatSecurityProperties`. Corrijo mi informe anterior: dije cuatro rutas, pero `/api/network/detected-devices` solo se consulta al exportar el informe, no en un temporizador, así que no la añadí.

**Prueba nueva:** `SessionPollPathsConsistencyTest` lee `network-monitoring.js` y `application.properties` reales, extrae las rutas que cada función llamada desde un `setInterval` consulta y exige que estén en `poll-paths`. También exige que las acciones del usuario (`/api/network/scan`, `/discover`, `/devices`, `/authorized-ranges`, `/api/cves/buscar`, invitaciones, `/proyectos`, `/dashboard`) **no** estén. Verificada con mutación: sin las tres rutas, falla y las nombra.

**Efectos aceptados** (anotados en `limitaciones.md`): `poll-paths` compara solo la ruta, así que `DELETE /api/network/history` tampoco cuenta como actividad; y estas rutas usan el límite `poll` (30/min) en lugar de `api-general` (120/min). La pantalla hace unas 6 peticiones por minuto a esas rutas, muy por debajo.

### G-1 — Matriz HTTP entre dos cuentas → **cerrada**

`CrossAccountIsolationIntegrationTest.anAuditorNeverReachesForeignOrUnassignedProjectsAreasOrFindings`. Dos cuentas con cadena completa (organización, proyecto, área, hallazgo) y un segundo proyecto de la cuenta A al que el auditor **no** está asignado. El auditor de A prueba:

1. La cadena completa de B (16 rutas).
2. La cadena del proyecto no asignado de A (16 rutas).
3. Identificadores inventados (16 rutas).
4. Rutas anidadas mezcladas: proyecto propio con área o hallazgo de B o del proyecto no asignado, proyecto de B con área propia, hallazgo ajeno bajo área propia (44 rutas: 10 + 10 + 16 + 4 + 4), más `/areas/{id}` con tres identificadores ajenos (3 rutas).

Total: **95 peticiones GET y POST. Todas responden 404** y las tablas `organizacion_cliente`, `auditoria`, `area_auditable`, `vulnerabilidad` y `asignacion_auditor_proyecto` quedan idénticas fila por fila. Un control positivo (8 rutas propias → 200) descarta que el 404 sea una sesión mal armada. Un segundo caso comprueba que el 404 de otra cuenta, el de otro proyecto y el de un ID inventado tienen el mismo cuerpo salvo el `correlationId`: no se puede distinguir "no existe" de "es de otro".

### G-2 — Manipulación de `cuentaId`, `rol` y riesgo → **cerrada**

`CrossAccountIsolationIntegrationTest.requestBodiesCannotChooseTheAccountTheRoleOrTheRisk`:

- Crear un proyecto enviando `cuentaId`, `cuenta_id`, `empresa.cuentaId`, `rol` y `organizacionId` de otra cuenta: el proyecto y la organización quedan en la cuenta del usuario y el rol del usuario sigue siendo `AUDITOR`.
- Crear un área y un hallazgo con `cuentaId` ajeno: quedan en la cuenta del usuario.
- F4-CA9: un hallazgo enviado con confidencialidad, integridad, disponibilidad y probabilidad en 10, pero con `impacto=0.01` y `nivelRiesgo=Bajo`, se guarda con impacto calculado por el servidor (mayor que 5) y nivel distinto de "Bajo".
- Valores fuera de rango (11 y -1) no se guardan. El plan pedía HTTP 400; la aplicación vuelve a mostrar el formulario con errores. Es un cambio de forma, no de seguridad, y está en el log.

### Observaciones menores (sin cambio)

- **Documentación en dos carpetas.** `hexora/docs/security/` (fases 0 a 2 y el inventario inicial) y `docs/security/` (el resto). No las unifiqué para no mover archivos que ya revisaste.
- **No hay sembrador de datos de demo.** El runbook crea los datos a mano. Si quieres una demo con datos precargados, hay que construir el sembrador; es trabajo nuevo que no pidió el plan.
- **`hexora/target` sigue versionado.** Cada compilación modifica archivos `.class` que git rastrea. No los incluyas en el commit: usa `git add` solo sobre `docs/` y `hexora/src/`.

## 6. Desviaciones

Registradas en `IMPLEMENTATION_LOG.md` (entrada del 2026-11-13, Fase 14).

## 7. Riesgos abiertos y preguntas para ti

1. **Sembrador de demo:** ¿lo quieres, o la demo se arma a mano como describe el runbook?
2. **Pendientes tuyos heredados:** F13-CA2 (PR de prueba con secreto falso), secreto `NVD_API_KEY` en GitHub, protección de `main`, retirar `hexora/target` del índice, confirmación visual de la CSP.
3. **No implementado por decisión tuya:** OAuth/OIDC (F9-CA1 a CA8) y PDF/Excel (F11-CA4, CA8).
4. **Commit.** Los cambios de la Fase 14 siguen sin commit en `seguridad/fase-14`. Incluyen un cambio de código de producción (3 líneas de configuración); revísalo antes de aprobar.

## 8. Pasos manuales para ti

1. Lee `docs/security/limitaciones.md` y `trazabilidad.md`; corrige lo que no refleje tu criterio.
2. Recorre `docs/security/runbook-demo.md` de principio a fin en una red de laboratorio y anota cualquier paso que falte (F14-CA4).
3. Reproduce el inventario:

   ```powershell
   cd hexora
   .\mvnw.cmd -B test "-Dtest=FinalEndpointInventoryIntegrationTest"
   Get-Content target\inventario-endpoints.md | Measure-Object -Line   # 92 líneas = 90 rutas + cabecera
   ```

4. Ejecuta la suite completa: `.\mvnw.cmd -B verify` (unos 4 minutos; necesita Docker).
5. Lista de cierre del Apéndice C del plan: el punto "recorrí manualmente" depende de las escenas 1, 2 y 4 del runbook.

**No continúes con trabajo nuevo hasta recibir `APROBADA FASE 14`.** Esta es la última fase del plan.
