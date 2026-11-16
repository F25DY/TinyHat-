# Auditoría y registros (Fase 12)

## Principios

- Cada evento relevante deja una fila en `audit_event` con usuario, cuenta, organización, IP, agente de usuario truncado, recurso y `correlation_id`. El `detalle` es JSON sin secretos.
- La tabla es de solo inserción. La aplicación no puede actualizar ni borrar filas.
- Los logs de archivo nunca contienen contraseñas, códigos TOTP, identificadores de sesión ni tokens.

## Catálogo de eventos

El catálogo vive en `AuditEventType`. La columna "Emisor" indica dónde se escribe la fila.

| Evento | Cuándo | Emisor |
|---|---|---|
| `LOGIN_OK`, `LOGIN_FAIL`, `LOGOUT` | Inicio de sesión, rechazo genérico, cierre | Manejadores de `SecurityConfiguration` |
| `MFA_ENROLLED`, `MFA_OK`, `MFA_FAIL`, `MFA_RESET` | Inscripción TOTP, verificación (login y reautenticación), error, reinicio | `MfaController` |
| `SESSION_EXPIRED` | Inactividad, tope absoluto, principal revocado | `SessionActivityFilter` |
| `SESSION_REPLACED` | Segundo inicio de sesión del mismo usuario | Estrategias de sesión expirada de ambas cadenas |
| `THROTTLED` | Bloqueo temporal de login por intentos fallidos | `LoginAttemptFilter`, `IpRateLimitFilter` |
| `RATE_LIMITED` | Cubo de fichas agotado (agregado por minuto) | `IpRateLimitFilter`, `UserRateLimitFilter` |
| `ACCESS_DENIED` | Rol insuficiente | Manejadores de acceso denegado |
| `NOT_FOUND_SCOPED` | 404 de recurso con ámbito | `ScopedNotFoundExceptionHandler` |
| `USER_CREATED`, `USER_VERIFIED`, `USER_INVITED` | Registro, verificación de correo, invitación | `UserRegistrationService` |
| `PASSWORD_RESET`, `PASSWORD_CHANGED` | Recuperación; contraseña definida al aceptar invitación | `UserRegistrationService` |
| `USER_SUSPENDED`, `USER_REACTIVATED` | Comando del propietario; suspensión detectada en una sesión viva | `OwnerOpsService`, `SessionActivityFilter` |
| `ROLE_CHANGED` | Cambio de rol u organización detectado en una sesión viva | `SessionActivityFilter` |
| `CAPABILITY_CHANGED` | Comando del propietario | `OwnerOpsService` |
| `OPS_COMMAND` | Cada comando del propietario | `OwnerOpsService` |
| `REPORT_CREATED`, `REPORT_VIEWED`, `REPORT_PUBLISHED`, `REPORT_UNPUBLISHED`, `REPORT_EXPORTED` | Ciclo de vida del informe | `ReportService` |
| `RANGE_DECLARED`, `SCAN_STARTED`, `SCAN_COMPLETED`, `SCAN_REJECTED`, `SCAN_FAILED` | Alcance y escaneo | `AuthorizedNetworkRangeService`, `NetworkMonitoringController` |
| `STARTUP_CONFIG` | Una vez por arranque, con la configuración efectiva sin secretos | `StartupConfigAuditor` |
| `OAUTH_LINKED` | Reservado | Sin emisor |

`OAUTH_LINKED` queda reservado porque la Fase 9 (OAuth2/OIDC) se descartó por decisión del usuario. Una prueba comprueba que nadie lo emite.

`ROLE_CHANGED` y la variante detectada de `USER_SUSPENDED` se registran cuando la sesión viva nota el cambio, porque en v1 no existe pantalla que cambie roles. Los comandos del propietario cubren la suspensión de usuarios y el cambio de capacidades.

## Comandos del propietario

Solo con `--tinyhat.ops.enabled=true` y `spring.main.web-application-type=none`. Cada uso deja `OPS_COMMAND`.

```
--tinyhat.ops.suspend-cuenta=<cuentaPublicId>
--tinyhat.ops.reactivate-cuenta=<cuentaPublicId>
--tinyhat.ops.suspend-usuario=<usuarioPublicId>
--tinyhat.ops.reactivate-usuario=<usuarioPublicId>
--tinyhat.ops.set-capability=<cuentaPublicId>:<modulo_red|informes_pdf|monitoreo_continuo>:<true|false>
```

Reactivar un usuario solo funciona si estaba suspendido. Nunca activa usuarios pendientes o invitados.

## Solo inserción

- `AuditEventRepository` extiende el marcador `Repository`, no `JpaRepository`. Solo ofrece guardar, buscar por id, contar y listar por cuenta.
- `AuditEvent` está anotada como inmutable en Hibernate.
- Solo `AuditService` usa el repositorio.
- `AuditAppendOnlyTest` verifica las tres reglas y que ninguna fuente contenga `UPDATE` o `DELETE` sobre la tabla. Equivale a las reglas ArchUnit del plan sin añadir una dependencia que no soporta la versión de Java del proyecto.
- En la base de datos, `database/grant-runtime-privileges.sql` concede al usuario de ejecución solo `SELECT` e `INSERT` sobre `audit_event`, y `SELECT`, `INSERT`, `UPDATE`, `DELETE` sobre el resto. Hay que volver a ejecutarlo después de cada migración que cree tablas.

## Higiene de logs

`logback-spring.xml` pasa todo mensaje por `%mask`, que oculta:

- Valores de `password`, `secret`, `token`, `api-key`, identificadores de sesión, `totp` y códigos de recuperación, en formas `clave=valor`, `clave: valor` y JSON.
- El valor completo de las cabeceras `Authorization`, `Cookie` y `Set-Cookie`.
- Códigos `code=NNNNNN`.
- Enlaces de un solo uso (`/verificar/...`, `/recuperar/...`, `/invitacion/...`).
- Cualquier palabra de 43 caracteres base64url, que es la longitud exacta de los tokens de 32 bytes que emite la aplicación.

Se escribe en consola y en un archivo rotativo (`logs/tinyhat.log`, 10 MB por archivo, 30 días, 500 MB en total). El directorio `logs/` está ignorado por git.

Excepción documentada: `ConsoleNotifier` (solo perfiles `dev` y `demo`, prohibido en `prod`) imprime los enlaces de un solo uso para que la demo sea usable. Lo hace con un logger propio que va solo a la consola y nunca al archivo.

Las peticiones de autenticación no se registran con su cuerpo, y ningún componente escribe identificadores de sesión.

## Detección de enumeración

`EnumerationDetector` cuenta los `NOT_FOUND_SCOPED` por usuario y minuto. Al superar `tinyhat.security.audit.enumeration-threshold` (20 por defecto) escribe una línea WARN, una vez por usuario y minuto. Los contadores viven en memoria.

## Retención

El parámetro `tinyhat.security.audit.retention-days` (365 por defecto, variable `TINYHAT_AUDIT_RETENTION_DAYS`) fija el objetivo documentado. En v1 no hay borrado automático. Cualquier purga futura deberá hacerse con la cuenta de migración, nunca con la de ejecución.
