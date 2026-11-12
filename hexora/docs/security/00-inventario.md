# TinyHat — Inventario de seguridad (Fase 0)

Fecha de inspección: 2026-11-05. Rama: `seguridad/fase-00`.

## Alcance, versiones y estado de ejecución

| Componente | Evidencia del repositorio/entorno |
|---|---|
| Java | POM requiere Java 25; runtime local observado: Java 27.0.1 |
| Spring Boot | 4.0.6 |
| Spring Security | No está en el POM ni existe configuración de seguridad en el código |
| Hibernate ORM | 7.2.12.Final |
| MySQL | `docker-compose.yml` declara `mysql:8.0`; el servidor no fue accesible durante la línea base |
| MySQL Connector/J | 9.7.0 |
| Flyway | 11.14.1; `spring-boot-starter-flyway` y `flyway-mysql` |
| Thymeleaf | 3.1.5.RELEASE (`thymeleaf-spring6`) |
| WebSocket | Spring WebSocket 7.0.7 |
| PDF | No se encontró una dependencia ni controlador de PDF |

El documento de implementación presupone Spring Boot 3.x/Spring Security 6.x/Hibernate 6.x. Las versiones reales principales son Boot 4/Spring 7/Hibernate 7; cualquier implementación posterior debe adaptar las APIs y registrar la desviación según R7.

El datasource está configurado para MySQL en `localhost:3307`, con credenciales referenciadas mediante variables de entorno. `spring.jpa.hibernate.ddl-auto=validate` está activo. Flyway tiene `baseline-on-migrate=true` y `baseline-version=2`; no se declara `spring.flyway.out-of-order=false`.

## Flyway y esquema

- Hay 16 archivos de migración en `src/main/resources/db/migration`, de V1 a V16. La última migración presente en el código es `V16__add_gateway_fields_to_detected_devices.sql`; no existe V17 en ese directorio.
- La primera ejecución de la línea base encontró MySQL inaccesible (`Communications link failure`). En la comprobación posterior solicitada por la persona, el contenedor `hexora-mysql` apareció `healthy`, el puerto local 3307 aceptó conexiones y la consulta de solo lectura a `flyway_schema_history` devolvió **V16, success=1**.
- No se ha aplicado ninguna migración durante este inventario. No se editaron el código ni el esquema.
- La persona informa que la migración V17 está en otra rama especializada en mejoras del módulo de red y solicita reservar V18 para evitar colisiones. La BD local inspeccionada todavía termina en V16, por lo que no se confirma que su esquema incluya V17. El plan original requiere detenerse si la última versión aplicada no es V17; la discrepancia debe resolverse o aprobarse explícitamente antes de las fases con migraciones.

Tablas identificadas en las migraciones: `empresa`, `auditoria`, `area_auditable`, `vulnerabilidad`, `cve`, `network_host`, `network_scan`, `port_scan_result`, `network_event`, `monitored_port`, `network_detected_device`, `network_discovery_event`, `network_discovery_snapshot` y `network_discovery_snapshot_device`.

## Mapa de endpoints

La columna «protección actual» se refiere a autenticación/autorización. No hay `SecurityFilterChain`, `@PreAuthorize`, sesión autenticada ni protección CSRF de Spring Security. Todos los mapeos listados carecen de protección de usuario.

| Método | Ruta | Controlador | Parámetros de recurso / observación | Protección actual |
|---|---|---|---|---|
| GET | `/` | `AuthController` | Página de acceso | Ninguna |
| GET | `/logout`, `/recuperar-contrasena` | `AuthController` | Ambos redirigen a `/` | Ninguna; GET logout no invalida sesión |
| POST | `/login` | `AuthController` | `email`, `password`; el método no valida credenciales y redirige a `/dashboard` | Ninguna |
| GET | `/dashboard`, `/inicio` | `DashboardController` | Dashboard | Ninguna |
| GET | `/proyectos`, `/proyectos/` | `ProyectosController` | Lista datos con `findAll()` | Ninguna |
| GET | `/proyectos/nuproyectos` | `ProyectosController` | Formulario de alta | Ninguna |
| POST | `/proyectos/guardar` | `ProyectosController` | Crea empresa y auditoría/proyecto | Ninguna |
| GET | `/proyectos/{id}` | `ProyectosController` | `{id}` es `Long` (auditoría/proyecto) | Ninguna |
| GET | `/proyectos/{id}/editar` | `ProyectosController` | `{id}` es `Long` | Ninguna |
| POST | `/proyectos/{id}/actualizar` | `ProyectosController` | `{id}` es `Long` | Ninguna |
| POST | `/proyectos/{id}/eliminar` | `ProyectosController` | `{id}` es `Long`; elimina dependencias | Ninguna |
| GET | `/proyectos/{idProyecto}/areas/nueva`, `/proyectos/{idProyecto}/departamentos/nuevo` | `AreaController` | `{idProyecto}` numérico | Ninguna |
| POST | `/proyectos/{idProyecto}/areas/guardar`, `/proyectos/{idProyecto}/departamentos/guardar` | `AreaController` | `{idProyecto}` numérico; crea área | Ninguna |
| GET | `/areas/{idArea}`, `/proyectos/{idProyecto}/departamentos/{idArea}` | `AreaController` | `{idArea}` numérico; la ruta anidada no valida que coincida con el proyecto | Ninguna |
| GET | `/areas/{idArea}/editar` | `AreaController` | `{idArea}` numérico | Ninguna |
| POST | `/areas/{idArea}/actualizar`, `/areas/{idArea}/eliminar` | `AreaController` | `{idArea}` numérico | Ninguna |
| GET | `/proyectos/{pid}/departamentos/{did}/vulnerabilidades/nueva` | `VulnerabilidadController` | `{pid}`, `{did}` numéricos | Ninguna |
| POST | `/proyectos/{pid}/departamentos/{did}/vulnerabilidades/guardar` | `VulnerabilidadController` | `{pid}`, `{did}` numéricos; recibe entidad JPA por `@ModelAttribute` | Ninguna |
| GET | `/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}/editar` | `VulnerabilidadController` | `{pid}`, `{did}`, `{vid}` numéricos | Ninguna |
| GET | `/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}` | `VulnerabilidadController` | `{pid}`, `{did}`, `{vid}` numéricos | Ninguna |
| POST | `/proyectos/{pid}/departamentos/{did}/vulnerabilidades/{vid}/actualizar` | `VulnerabilidadController` | IDs numéricos; recibe entidad JPA por `@ModelAttribute` | Ninguna |
| POST | `/proyectos/{pid}/departamentos/{did}/vulnerabilidades/eliminar/{vid}` | `VulnerabilidadController` | IDs numéricos | Ninguna |
| GET | `/api/cves/buscar` | `CveController` | Query `q`; devuelve entidad `Cve` | Ninguna |
| GET | `/api/cves/{codigo}/detalle` | `CveController` | `{codigo}` CVE; devuelve entidad `Cve` | Ninguna |
| GET | `/network-monitoring`, `/network-monitoring/history` | `NetworkMonitoringPageController` | Páginas de monitoreo | Ninguna |
| POST | `/api/network/scan`, `/api/network/discover` | `NetworkMonitoringController` | Escaneo por `hostId`/puertos o descubrimiento de red local | Ninguna |
| GET | `/api/network/history` | `NetworkMonitoringController` | Query opcional `hostId`, `limit` | Ninguna |
| GET/POST | `/api/network/hosts`, `/api/network/devices` | `NetworkMonitoringController` | Listado/registro de dispositivos | Ninguna |
| POST/PUT | `/api/network/hosts/{hostId}/ports`, `/api/network/devices/{hostId}/ports` | `NetworkMonitoringController` | `{hostId}` numérico; gestión de puertos | Ninguna |
| PUT/DELETE | `/api/network/hosts/{hostId}/ports/{port}`, `/api/network/devices/{hostId}/ports/{port}` | `NetworkMonitoringController` | `{hostId}`, `{port}` numéricos | Ninguna |
| DELETE | `/api/network/hosts/{hostId}`, `/api/network/devices/{hostId}` | `NetworkMonitoringController` | `{hostId}` numérico | Ninguna |
| DELETE | `/api/network/history`, `/api/network/history/scans`, `/api/network/devices/{hostId}/history` | `NetworkMonitoringController` | Borra historial; el segundo recibe IDs de escaneos | Ninguna |
| GET | `/api/network/detected-devices`, `/api/network/gateway`, `/api/network/discoveries/latest`, `/api/network/discoveries/history`, `/api/network/status` | `NetworkMonitoringController` | Lectura del inventario/estado local | Ninguna |
| WebSocket | `/ws/network` | `NetworkWebSocketConfiguration` | Handshake sin `HandshakeInterceptor` de autenticación ni validación explícita de `Origin` | Ninguna |

`NetworkMethodNotAllowedFilter` trata métodos HTTP no permitidos para rutas de red; no autentica ni autoriza usuarios. Las rutas no están separadas por cuenta, organización o proyecto. La cadena `/api/agent/**` no tiene implementación.

## Acceso a datos, entradas y riesgos observados

### Acceso por ID y ámbito

- Se encontraron llamadas `findById` directamente en `AreaController`, `ProyectosController` y `VulnerabilidadController` para entidades de negocio.
- `NetworkMonitoringService` también usa `findById` para hosts. No se encontraron llamadas a `getById` ni `getReferenceById`.
- Los IDs de negocio expuestos en rutas son numéricos. No hay consultas por `cuenta_id` ni modelo de tenant.
- `ProyectosController` lista con `findAll()` empresas y auditorías; no hay filtro de usuario/organización.

### Consultas SQL

- No se encontraron `nativeQuery=true`, `createNativeQuery` ni `JdbcTemplate` en el código Java inspeccionado.
- Los repositorios usan métodos derivados de Spring Data. En la fase de aislamiento deberá revisarse cada método para agregar ámbito de cuenta/organización.

### Controladores y entidades

- `VulnerabilidadController` enlaza la entidad JPA `Vulnerabilidad` desde el formulario mediante `@ModelAttribute`, tanto para guardar como para actualizar.
- `CveController` devuelve directamente `Cve`/`List<Cve>` en API.
- Los controladores de red reciben DTOs `@RequestBody`; la identidad/cuenta no se obtiene de un principal autenticado.

### Plantillas y JavaScript

- No se encontraron `th:utext` ni expresiones de selección `[(...)]`.
- Hay scripts inline en `detalle-area.html`, `detalleproyecto.html` y `nuvulnerabilidad.html`; `proyectos.html` contiene un bloque inline comentado.
- `network-monitoring.js` y `network-monitoring-history.js` construyen partes del DOM con `innerHTML`, incluyendo plantillas que interpolan datos del servidor. `network-monitoring.js` tiene un helper `escapeHtml` en una de las salidas; cada interpolación debe revisarse en Fase 11.
- `cve-buscador.js` indica que inserta texto recibido mediante `textContent` y no `innerHTML`.
- No se encontraron `outerHTML` ni `document.write`.

### Procesos del sistema

- No se encontraron `Runtime.exec` ni `exec(...)`.
- `LocalNetworkDiscoveryService` invoca PowerShell con `ProcessBuilder` y argumentos separados; el texto fijo de PowerShell concatena el índice de una interfaz obtenida del sistema operativo.
- `NetworkReachabilityProbe` ejecuta `ping.exe` con argumentos separados y una dirección `InetAddress`.
- `NmapHostDiscovery` ejecuta el binario Nmap con argumentos separados; el destino se calcula desde la subred local detectada.
- No se observó `sh -c` ni `cmd /c` en estas invocaciones. La validación y autorización del alcance de escaneo sigue pendiente.

## Autenticación, contraseñas y secretos

- `AuthController` recibe `email` y `password` en `POST /login`, pero no autentica ni persiste información: redirige directamente al dashboard.
- No hay entidad/tabla de usuarios, repositorio de usuarios, encoder de contraseñas ni formato de hash. Por tanto, no existe flujo real de contraseña ni hash que migrar; antes de habilitar autenticación deberá definirse alta inicial/reset según Fase 3.
- En `application.properties`, los valores de conexión a BD se toman de variables de entorno. `docker-compose.yml` también usa variables de entorno para el usuario y contraseñas de MySQL.
- Existe `hexora/.env` local, ignorado por Git y no versionado. Se inspeccionaron únicamente nombres de variables, nunca sus valores. Contiene configuración sensible de BD (`DB_ROOT_PASSWORD`, `DB_PASSWORD`); no se rotó ni copió ningún valor.
- `NVD_API_KEY` y `MYMEMORY_EMAIL` se leen como configuración de entorno por los servicios; los valores no se imprimen en este inventario.
- Esta inspección de configuración no sustituye un escaneo exhaustivo del historial con gitleaks. No se verificó el historial Git.

## Servicios externos y auditoría

- `CveService` consulta la URL HTTPS fija de NVD 2.0; busca primero en la caché local y añade `NVD_API_KEY` como cabecera cuando está configurada. La búsqueda de usuario se pasa como parámetro URI.
- `TraductorService` consulta la URL HTTPS fija de MyMemory. No hay timeouts configurados de forma explícita en esos `RestClient`.
- No se encontraron servicios o tablas de auditoría de seguridad ni IDs de correlación en el flujo web actual.
- No hay dependencias ni rutas de PDF/informes identificadas.

## Evidencia del alcance inspeccionado

- Dependencias y versiones: `pom.xml`; runtime Java inspeccionado con `java -version`.
- Configuración: `src/main/resources/application.properties`, `docker-compose.yml`, nombres de variables de `.env`.
- Endpoints: controladores en `src/main/java/com/hexora/hexora/controller/` y `src/main/java/com/hexora/network/controller/`; handshake en `src/main/java/com/hexora/network/websocket/NetworkWebSocketConfiguration.java`.
- Esquema: `src/main/resources/db/migration/`.
- No se modificó código ni esquema como parte de este inventario.
