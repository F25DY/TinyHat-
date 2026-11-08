# Fase 3 — Núcleo de Spring Security

## 1. Resumen

Se completó la configuración de autenticación y autorización de Fase 3:

- Dos cadenas de seguridad separan la API JSON de las rutas web con login por formulario.
- Acceso por defecto denegado, CSRF, roles explícitos, login genérico, logout solo por POST, sesiones concurrentes y cabeceras seguras.
- `TinyHatPrincipal`, carga de usuario y auditoría con correlation ID.
- Controladores de negocio protegidos con `@PreAuthorize`; formularios y `fetch` envían CSRF.
- Las pruebas de integración que arrancaban Spring ahora apuntan a MySQL desechable y no a la base del desarrollador.

## 2. Migraciones

No se ejecutaron migraciones en la base compartida.

Las migraciones V18–V24 se ejecutaron en instancias MySQL de Testcontainers durante las pruebas. V18 detecta el nombre real de la FK auditoría-organización antes de reemplazarla; el mapeo final usa `organizacion_cliente_id`.

La base compartida `hexora_db` continúa en estado parcial: V17 exitosa, V18 fallida. El dump `Dump20261108.sql`, inspeccionado en solo lectura, también registra V18 con `success=0`, conserva `auditoria.empresa_id` y la FK anterior, y contiene los cambios parciales de `cuenta` y `organizacion_cliente`. Por tanto, es una copia del estado parcial y no un respaldo previo a V18. No se importó.

No ejecutar la aplicación ni Flyway contra el datasource predeterminado hasta acordar y validar la recuperación.

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

- Se documentó el uso de V24 para normalizar `monitored_port`, aunque el mapa original reservaba V24 para MFA. La numeración futura debe actualizarse antes de desarrollar V25–V27. Ver [IMPLEMENTATION_LOG.md](../IMPLEMENTATION_LOG.md).
- La FK de auditoría tenía un nombre distinto al supuesto originalmente. V18 ahora resuelve el nombre por metadatos y fue probada desde un esquema limpio V17.
- El dump `Dump20261108.sql` fue localizado mediante la ruta proporcionada e inspeccionado solo para metadatos/DDL relevantes. No fue importado ni ejecutado.

## 6. Riesgos y pendientes

- La base compartida no está migrada y mantiene una fila de V18 fallida y cambios DDL parciales. El dump disponible refleja el mismo estado, por lo que no permite restaurar el estado anterior a V18. Se necesita un respaldo pre-V18 o un procedimiento de recuperación probado primero en una copia aislada.
- La unificación canónica de activos de red y la validación compuesta que impide cruces de cuenta siguen pendientes. La normalización de puertos es parcial hasta revisar el respaldo y probar el traspaso de datos.
- No avanzar a Fase 4 ni aplicar V18–V24 a la base compartida hasta revisar el dump, acordar la recuperación y completar las migraciones de esquema requeridas.

## 7. Validación manual

Las pruebas MockMvc cubren login, logout, CSRF, respuestas API/web, cabeceras, rutas denegadas y auditoría. No se recomienda iniciar la aplicación manualmente con el perfil/datasource actual porque Flyway intentaría operar sobre la base compartida.
