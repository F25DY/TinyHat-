# TinyHat — Inventario final de endpoints (Fase 14)

Repetición de la Fase 0 sobre el código de `seguridad/fase-14`. Compara con `hexora/docs/security/00-inventario.md`, que describe el estado inicial.

## 1. Resultado

| Medida | Fase 0 (inicial) | Fase 14 (final) |
|---|---|---|
| Mapeos con método y ruta | 35 filas agrupadas | **90** combinaciones método + ruta |
| Protección de autenticación/autorización | Ninguna en 34 de 35 filas. No había Spring Security, sesión, CSRF ni `@PreAuthorize`. `POST /login` redirigía al dashboard sin validar credenciales. | **73 protegidas** y **17 públicas declaradas a propósito** (ver §3). Ninguna ruta queda sin regla. |
| API (`/api/**`) | Sin protección | 39 rutas; sin sesión responden **401 JSON**, nunca redirección |
| Web | Sin protección | 34 rutas protegidas; sin sesión redirigen a `/login` |
| Regla final de las cadenas | No existía | `anyRequest().denyAll()` en las dos cadenas; `/api/agent/**` reservado con `denyAll()` |
| IDs en rutas de negocio | `Long` secuencial | UUID (`public_id`) en proyectos, áreas, hallazgos, informes y organizaciones. **El módulo de red conserva `{hostId}` numérico** (ver `limitaciones.md`). |
| WebSocket `/ws/network` | Sin autenticación ni validación de `Origin` | Exige rol `AUDITOR` en la cadena web, sesión con MFA completo, origen exacto y capacidad `modulo_red` |

## 2. Cómo se generó (evidencia reproducible)

La tabla de §5 **no se escribió a mano**. La produce la prueba `FinalEndpointInventoryIntegrationTest`, que recorre `RequestMappingHandlerMapping` y, para cada mapeo de `com.hexora.*`:

1. Lee la regla de método (`@PreAuthorize`) del método o de la clase.
2. Si la ruta está en la lista cerrada `PUBLIC_ROUTES` de la prueba, no la invoca (para no crear datos) y comprueba que se declaró pública con `permitAll()` o que es `AuthController`.
3. Si no, la invoca **sin sesión** (con CSRF válido en métodos no seguros, para que el rechazo observado sea el de autorización y no el del token) y exige 401 (`/api/**`) o redirección a `/login` (web).
4. Falla si una ruta pública declarada ya no existe, o si una ruta nueva no es rechazada.

Agregar una ruta pública exige editar esa lista: es una decisión de seguridad visible en la revisión. Comando:

```powershell
cd hexora
.\mvnw.cmd -B test "-Dtest=FinalEndpointInventoryIntegrationTest"
# Salida: hexora\target\inventario-endpoints.md
```

La misma prueba verifica que `/actuator/**`, `/h2-console`, `/ws/network`, rutas inexistentes y `/api/agent/**` también se rechazan sin sesión. Esas rutas no figuran en la tabla porque no son mapeos de controlador.

## 3. Rutas públicas (17 filas, 9 rutas distintas)

Son las únicas que responden sin sesión. Cada una tiene una razón:

| Ruta | Para qué |
|---|---|
| `GET /`, `GET /login`, `GET /recuperar-contrasena` | Páginas de entrada (`AuthController`) |
| `GET/POST /registro` | Alta de auditores (rol fijo en servidor; respuesta idéntica exista o no el correo; Fase 8) |
| `GET /verificar/{token}`, `GET /verificar-correo/{token}` | Verificación de correo con token de un solo uso |
| `GET/POST /invitacion/{token}` | Aceptación de invitación de `CLIENTE_ADMIN` |
| `GET/POST /recuperar`, `GET/POST /recuperar/{token}` | Recuperación de contraseña (respuesta neutra; token de un solo uso) |

Las filas duplicadas (`POST /registro` aparece dos veces) son las variantes JSON y formulario del mismo método. Los recursos estáticos (`/css/**`, `/js/**`, `/img/**`, `/imagen/**`, `/favicon.ico`) y `/error` son públicos por configuración de la cadena web y no son mapeos de controlador.

## 4. Rutas fuera de los controladores

Estas las define la configuración de Spring Security, no un `@RequestMapping`:

| Ruta | Regla |
|---|---|
| `POST /login` | `formLogin`, con limitador de intentos, CSRF y mensaje de error único |
| `POST /logout` | Solo `POST`, con CSRF; `GET /logout` no cierra sesión |
| `/ws/network` | `hasRole('AUDITOR')` + interceptor de handshake (MFA, origen, capacidad) |
| `/actuator/**` | Cae en `anyRequest().denyAll()`. Verificado sin sesión por la prueba |
| `/api/agent/**` | `denyAll()` explícito; prefijo reservado |

## 5. Tabla completa (generada)

Regla de método = lo que declara `@PreAuthorize`. Además, cada ruta pasa por la regla de la cadena de seguridad (`SecurityConfiguration`), que repite el rol requerido: **hay dos controles independientes por ruta**.

| Método | Ruta | Controlador#método | Regla de método | Sin sesión |
|---|---|---|---|---|
| GET | `/` | AuthController#index | `(sin regla)` | pública (declarada) |
| GET | `/api/cves/buscar` | CveController#buscar | `hasRole('AUDITOR')` | 401 |
| GET | `/api/cves/{codigo}/detalle` | CveController#detalle | `hasRole('AUDITOR')` | 401 |
| GET | `/api/informes/{publicId}` | ReportController#get | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 401 |
| GET | `/api/informes/{publicId}/contenido` | ReportController#content | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 401 |
| GET | `/api/informes/{publicId}/descarga` | ReportController#download | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 401 |
| POST | `/api/informes/{publicId}/despublicar` | ReportController#unpublish | `hasRole('AUDITOR')` | 401 |
| POST | `/api/informes/{publicId}/publicar` | ReportController#publish | `hasRole('AUDITOR')` | 401 |
| POST | `/api/network/authorized-ranges` | NetworkMonitoringController#declareAuthorizedRange | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/detected-devices` | NetworkMonitoringController#detectedDevices | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/devices` | NetworkMonitoringController#hosts | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/network/devices` | NetworkMonitoringController#registerHost | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/devices/{hostId}` | NetworkMonitoringController#removeManagedHost | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/devices/{hostId}/history` | NetworkMonitoringController#clearHostHistory | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/network/devices/{hostId}/ports` | NetworkMonitoringController#addMonitoredPort | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| PUT | `/api/network/devices/{hostId}/ports` | NetworkMonitoringController#updateMonitoredPorts | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/devices/{hostId}/ports/{port}` | NetworkMonitoringController#removeMonitoredPort | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| PUT | `/api/network/devices/{hostId}/ports/{port}` | NetworkMonitoringController#setMonitoredPortEnabled | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/network/discover` | NetworkMonitoringController#discoverLocalNetwork | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/discoveries/history` | NetworkMonitoringController#discoveryHistory | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/discoveries/latest` | NetworkMonitoringController#latestDiscovery | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/gateway` | NetworkMonitoringController#gateway | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/history` | NetworkMonitoringController#clearHistory | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/history` | NetworkMonitoringController#history | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/history/scans` | NetworkMonitoringController#deleteSelectedScans | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/hosts` | NetworkMonitoringController#hosts | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/network/hosts` | NetworkMonitoringController#registerHost | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/hosts/{hostId}` | NetworkMonitoringController#removeManagedHost | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/network/hosts/{hostId}/ports` | NetworkMonitoringController#addMonitoredPort | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| PUT | `/api/network/hosts/{hostId}/ports` | NetworkMonitoringController#updateMonitoredPorts | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| DELETE | `/api/network/hosts/{hostId}/ports/{port}` | NetworkMonitoringController#removeMonitoredPort | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| PUT | `/api/network/hosts/{hostId}/ports/{port}` | NetworkMonitoringController#setMonitoredPortEnabled | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/projects` | NetworkMonitoringController#visibleProjects | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/projects/{projectPublicId}/authorized-ranges` | NetworkMonitoringController#authorizedRanges | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| PUT | `/api/network/projects/{projectPublicId}/continuous-monitoring` | NetworkMonitoringController#setContinuousMonitoring | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/network/scan` | NetworkMonitoringController#scan | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| GET | `/api/network/status` | NetworkMonitoringController#status | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 401 |
| POST | `/api/organizaciones/{publicId}/invitaciones` | InvitacionApiController#invitar | `hasRole('AUDITOR')` | 401 |
| GET | `/api/proyectos/{projectPublicId}/informes` | ReportController#list | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 401 |
| POST | `/api/proyectos/{projectPublicId}/informes` | ReportController#create | `hasRole('AUDITOR')` | 401 |
| GET | `/areas/{areaPublicId}` | AreaController#detalleArea | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| GET | `/dashboard` | DashboardController#dashboard | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/inicio` | DashboardController#dashboard | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/invitacion/{token}` | UserAccountLifecycleWebController#invitationPage | `permitAll()` | pública (declarada) |
| POST | `/invitacion/{token}` | UserAccountLifecycleWebController#acceptInvitationForm | `permitAll()` | pública (declarada) |
| POST | `/invitacion/{token}` | UserAccountLifecycleWebController#acceptInvitationJson | `permitAll()` | pública (declarada) |
| GET | `/login` | AuthController#loginPage | `(sin regla)` | pública (declarada) |
| GET | `/mfa/enroll` | MfaController#enrollPage | `hasAuthority('ROLE_PRE_MFA')` | 302 → /login |
| POST | `/mfa/enroll/confirm` | MfaController#confirmEnrollment | `hasAuthority('ROLE_PRE_MFA')` | 302 → /login |
| POST | `/mfa/enroll/start` | MfaController#startEnrollment | `hasAuthority('ROLE_PRE_MFA')` | 302 → /login |
| GET | `/mfa/reauth` | MfaController#reauthenticationPage | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| POST | `/mfa/reauth` | MfaController#reauthenticate | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| GET | `/mfa/reset` | MfaController#resetPage | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| POST | `/mfa/reset` | MfaController#reset | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| GET | `/mfa/verify` | MfaController#verifyPage | `hasAuthority('ROLE_PRE_MFA')` | 302 → /login |
| POST | `/mfa/verify` | MfaController#verify | `hasAuthority('ROLE_PRE_MFA')` | 302 → /login |
| GET | `/network-monitoring` | NetworkMonitoringPageController#networkMonitoring | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 302 → /login |
| GET | `/network-monitoring/history` | NetworkMonitoringPageController#networkDiscoveryHistory | `hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)` | 302 → /login |
| GET | `/proyectos` | ProyectosController#listarProyectos | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| GET | `/proyectos/` | ProyectosController#listarProyectos | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| POST | `/proyectos/guardar` | ProyectosController#guardarProyectos | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/nuproyectos` | ProyectosController#crearProyecto | `hasRole('AUDITOR')` | 302 → /login |
| POST | `/proyectos/{proyectoPublicId}/areas/guardar` | AreaController#guardarArea | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{proyectoPublicId}/areas/nueva` | AreaController#crearArea | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}` | AreaController#detalleAreaAnidada | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| POST | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/actualizar` | AreaController#actualizarArea | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/editar` | AreaController#editarArea | `hasRole('AUDITOR')` | 302 → /login |
| POST | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/eliminar` | AreaController#eliminarArea | `hasRole('AUDITOR')` | 302 → /login |
| POST | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/guardar` | VulnerabilidadController#guardarVulnerabilidad | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/nueva` | VulnerabilidadController#crearVulnerabilidad | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}` | VulnerabilidadController#detalleVulnerabilidad | `hasRole('AUDITOR')` | 302 → /login |
| POST | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}/actualizar` | VulnerabilidadController#actualizarVulnerabilidad | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}/editar` | VulnerabilidadController#editarVulnerabilidad | `hasRole('AUDITOR')` | 302 → /login |
| POST | `/proyectos/{proyectoPublicId}/departamentos/{areaPublicId}/vulnerabilidades/{hallazgoPublicId}/eliminar` | VulnerabilidadController#eliminarVulnerabilidad | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{publicId}` | ProyectosController#detalleProyecto | `hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')` | 302 → /login |
| POST | `/proyectos/{publicId}/actualizar` | ProyectosController#proyectoActualizar | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/proyectos/{publicId}/editar` | ProyectosController#proyectoEditar | `hasRole('AUDITOR')` | 302 → /login |
| POST | `/proyectos/{publicId}/eliminar` | ProyectosController#eliminarProyecto | `hasRole('AUDITOR')` | 302 → /login |
| GET | `/recuperar` | UserAccountLifecycleWebController#recuperarPage | `permitAll()` | pública (declarada) |
| POST | `/recuperar` | UserAccountLifecycleWebController#recuperarForm | `permitAll()` | pública (declarada) |
| POST | `/recuperar` | UserAccountLifecycleWebController#recuperarJson | `permitAll()` | pública (declarada) |
| GET | `/recuperar-contrasena` | AuthController#recuperarContrasena | `(sin regla)` | pública (declarada) |
| GET | `/recuperar/{token}` | UserAccountLifecycleWebController#resetPasswordPage | `permitAll()` | pública (declarada) |
| POST | `/recuperar/{token}` | UserAccountLifecycleWebController#resetPasswordForm | `permitAll()` | pública (declarada) |
| POST | `/recuperar/{token}` | UserAccountLifecycleWebController#resetPasswordJson | `permitAll()` | pública (declarada) |
| GET | `/registro` | UserAccountLifecycleWebController#registroPage | `permitAll()` | pública (declarada) |
| POST | `/registro` | UserAccountLifecycleWebController#registerForm | `permitAll()` | pública (declarada) |
| POST | `/registro` | UserAccountLifecycleWebController#registerJson | `permitAll()` | pública (declarada) |
| GET | `/verificar-correo/{token}` | UserAccountLifecycleWebController#verifyEmailAlias | `permitAll()` | pública (declarada) |
| GET | `/verificar/{token}` | UserAccountLifecycleWebController#verifyEmail | `permitAll()` | pública (declarada) |
