# Fase 4 — Aislamiento por cuenta y organización

## 1. Resumen

Se implementó el aislamiento de datos de Fase 4 sobre la rama `seguridad/fase-01`, sin cambiar `main` ni aplicar migraciones o escrituras a la BD compartida.

- Las solicitudes autenticadas establecen el tenant desde el `TinyHatPrincipal`; el filtro Hibernate se habilita durante la solicitud y se deshabilita al terminar.
- Las entidades con ámbito de cuenta fuerzan el `cuenta_id` activo en persistencia y rechazan asignaciones cruzadas.
- Proyectos, áreas y hallazgos se consultan mediante UUID público y verifican cuenta, asignación del auditor u organización del cliente.
- Los procesos programados ejecutan una transacción y contexto separados por cuenta activa.
- Las operaciones de limpieza de red acotan también las consultas de borrado por cuenta.
- Las sesiones WebSocket se asocian a una cuenta; solo se publica a sesiones autenticadas del tenant activo.

## 2. Criterios de aceptación

| ID | Cómo se verifica | Prueba | Resultado |
|---|---|---|---|
| F4-CA1 | Las cargas por clave y consultas de repositorio no devuelven recursos de otra cuenta | `hibernateFilterScopesKeyLookupsAndWritesUseOnlyTheActiveTenant` | PASA |
| F4-CA2 | La persistencia asigna el tenant actual y rechaza una cuenta falsificada | `hibernateFilterScopesKeyLookupsAndWritesUseOnlyTheActiveTenant` | PASA |
| F4-CA3 | Un auditor solo ve proyectos asignados; un administrador cliente solo ve proyectos de su organización | `auditorsAndClientAdminsOnlySeeProjectsWithinTheirAssignmentOrOrganization` | PASA |
| F4-CA4 | Limpiar el historial de una cuenta no borra el historial de otra | `clearingNetworkHistoryDeletesOnlyTheActiveTenantHistory` | PASA |
| F4-CA5 | Las tareas programadas se ejecutan con ámbito de cuenta y respetan el bloqueo/desactivación | `NetworkMonitoringSchedulerTest` | PASA |
| F4-CA6 | El broadcast WebSocket se limita al tenant activo | `publishesOnlyToSessionsInTheActiveTenant` | PASA |
| F4-CA7 | Las solicitudes HTTP de red reciben el tenant del principal y conservan la protección de rutas | `NetworkMonitoringWebTest` | PASA |
| F4-CA8 | Se conserva la regresión de autenticación, configuración y carga de aplicación | `PhaseThreeSecurityIntegrationTest`, `HexoraApplicationTests` | PASA |

## 3. Pruebas

Comando, ejecutado desde `hexora/`:

```powershell
.\mvnw.cmd -q "-Dmaven.compiler.useIncrementalCompilation=false" test
```

Resultado: **100 pruebas, 0 fallos, 0 errores y 1 omitida; código de salida 0**. Las pruebas de integración que requieren base de datos usan MySQL desechable.

## 4. Alcance y decisiones

- No se creó ni aplicó una migración nueva; la implementación usa el esquema existente hasta V24.
- No se ejecutó la aplicación contra la BD compartida ni se realizaron escrituras allí.
- No se encontró una ruta de publicación/consulta de informes en controladores. El repositorio de informes dispone de consultas visibles acotadas, pero la cobertura de acceso a informes deberá añadirse si se expone ese flujo.
- El módulo de red conserva IDs numéricos en sus rutas y DTOs. La resolución de recursos está protegida por el filtro de cuenta y las operaciones de borrado llevan predicado explícito de `cuenta_id`; estos IDs no se tratan como autorización. La conversión a UUID público no formó parte de esta fase.

## 5. Estado

Implementación y regresión automatizada completadas. La fase queda lista para revisión y aprobación del usuario. La unificación canónica de activos de red y la integración opcional del catálogo CVE siguen siendo trabajo de esquema posterior, fuera del alcance de este aislamiento.
