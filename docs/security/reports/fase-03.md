# Fase 3 — Núcleo de Spring Security

## 1. Resumen

Se completó la configuración de autenticación y autorización de Fase 3:

- Dos cadenas de seguridad separan la API JSON de las rutas web con login por formulario.
- Acceso por defecto denegado, CSRF, roles explícitos, login genérico, logout solo por POST, sesiones concurrentes y cabeceras seguras.
- `TinyHatPrincipal`, carga de usuario y auditoría con correlation ID.
- Controladores de negocio protegidos con `@PreAuthorize`; formularios y `fetch` envían CSRF.
- Las pruebas de integración que arrancaban Spring ahora apuntan a MySQL desechable y no a la base del desarrollador.

## 2. Migraciones

Las migraciones V18–V24 se ejecutaron en instancias MySQL de Testcontainers durante las pruebas. V18 detecta los nombres reales de las FKs antiguas y puede continuar desde el estado parcial hallado.

Tras crear snapshots previos, se ejecutó Flyway repair para eliminar el registro fallido y luego se aplicaron V18–V24 a `hexora_db`, con cada objetivo explícitamente limitado a la versión prevista. El último snapshot, en `C:\Users\Fredy\OneDrive\Documentos\dumps\Dump20261108_pre_v19_v24.sql`, se restauró en MySQL desechable y el tramo V19–V24 se validó allí antes de la aplicación compartida. El dump `Dump20261108.sql` inspeccionado inicialmente reflejaba V18 fallida y no fue importado.

La base está en V24, todas las versiones registradas son exitosas, y los conteos originales se conservaron. Se verificó que las relaciones usen `organizacion_cliente_id`, que existan 36 FKs de cuenta, que los cruces comprobados sean cero, y que los puertos 21–24 sigan habilitados tras normalizarse. No se crearon ni aplicaron versiones posteriores a V24.

## 3. Criterios de aceptación

| ID | Cómo se verifica | Prueba | Resultado |
|---|---|---|---|
| F3-CA1 | Recorre los mapeos y exige regla de autorización explícita o seguridad de método | `unlistedRoutesAreDeniedAndEveryControllerRouteHasExplicitMethodSecurity` | PASA |
| F3-CA2 | Sin sesión, rutas web redirigen y API responde 401 JSON | `unauthenticatedWebRedirectsAndApiReturnsJson401` | PASA |
| F3-CA3 | Verifica CSRF en escrituras y logout; escritura sin token se rechaza | `csrfProtectsWritesAndSecurityHeadersArePresent`, `successfulLoginAuditsAndLogoutRequiresPostAndCsrf` | PASA |
| F3-CA4 | Login inexistente y contraseña errónea dan respuesta equivalente | `loginFailuresHaveIdenticalResponsesAndAreAuditedWithCorrelationIds` | PASA |
| F3-CA5 | Logout GET no cierra sesión; POST invalida sesión y cookie | `successfulLoginAuditsAndLogoutRequiresPostAndCsrf` | PASA |
| F3-CA6 | Rutas no listadas se deniegan | `unlistedRoutesAreDeniedAndEveryControllerRouteHasExplicitMethodSecurity` | PASA |
| F3-CA7 | Comprueba cabeceras de seguridad | `csrfProtectsWritesAndSecurityHeadersArePresent` | PASA |
| F3-CA8 | Login correcto/fallido se audita con correlation ID | `successfulLoginAuditsAndLogoutRequiresPostAndCsrf`, `loginFailuresHaveIdenticalResponsesAndAreAuditedWithCorrelationIds` | PASA |

## 4. Pruebas

Comando: `.\mvnw.cmd test`

Resultado: **81 pruebas, 0 fallos, 0 errores, 1 omitida; BUILD SUCCESS**.

Todos los contextos con JPA/Flyway usaron Testcontainers. Los registros muestran URLs MySQL con puertos efímeros; no hubo conexión a `127.0.0.1:3307` ni a `hexora_db`.

## 5. Desviaciones

- V24 normaliza `monitored_port`, desplazando el número que el plan original reservaba para MFA. MFA y futuras migraciones deben comenzar en una versión libre posterior; se propone V25 para MFA. Ver [IMPLEMENTATION_LOG.md](../IMPLEMENTATION_LOG.md).
- La FK de auditoría, área y vulnerabilidad tenían nombres generados distintos a los supuestos originalmente. V18 resuelve esos nombres por metadatos y se probó en un clon aislado del dump y desde un esquema limpio.
- V19 creó las tablas de usuario y asignaciones sin insertar usuarios. No se alteró ni eliminó ningún usuario existente; no había registros en `usuario`.

## 6. Riesgos y pendientes

- La base compartida está en V24. El snapshot previo a V18 y el snapshot previo a V19–V24 se conservaron en `C:\Users\Fredy\OneDrive\Documentos\dumps\Dump20261108_pre_recovery.sql` y `C:\Users\Fredy\OneDrive\Documentos\dumps\Dump20261108_pre_v19_v24.sql`.
- La unificación canónica de activos de red y el vínculo opcional al catálogo CVE manteniendo captura manual aún no están implementados; requieren migraciones posteriores y cambios de aplicación.
- El mapa original reservaba V24 para MFA, pero ese número ya lo ocupa la normalización de puertos. Asignar MFA y demás versiones nuevas desde V25.

## 7. Validación manual

Las pruebas MockMvc cubren login, logout, CSRF, respuestas API/web, cabeceras, rutas denegadas y auditoría. `HexoraApplicationTests` pasó (1/1) con JPA/Hibernate validando el esquema. Las seis migraciones V19–V24 se aplicaron primero a una restauración desechable y después a la base compartida mediante Flyway.
