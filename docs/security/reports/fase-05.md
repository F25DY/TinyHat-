# Fase 5 — Ciclo de vida de sesión

## 1. Resumen

Se implementó el control de cookies y ciclo de vida de sesión sobre `seguridad/fase-01`. La cuenta del principal autenticado sigue siendo la autoridad de tenant; este cambio endurece cuánto y bajo qué condiciones una sesión puede seguir activa.

- Cookie de sesión con nombre `TINYHAT_SID`, atributos `HttpOnly`, `Secure` y `SameSite=Strict`; el seguimiento de sesiones se limita a cookies.
- Rotación de ID al autenticar y registro de marcas de creación/última actividad.
- Expiración por cinco minutos de inactividad del usuario y por 120 minutos de duración absoluta.
- Los sondeos configurados en el servidor no actualizan la actividad. El timeout del contenedor queda como respaldo de 10 minutos.
- Una nueva sesión del mismo usuario reemplaza la anterior.
- En cada solicitud autenticada se revalidan estado de usuario/cuenta, rol y organización. Suspensiones o cambios invalidan la sesión en la siguiente petición.
- La expiración se audita. La destrucción de una sesión publica un evento interno que cierra los WebSockets asociados.

## 2. Criterios de aceptación

| ID | Cómo se verifica | Prueba | Resultado |
|---|---|---|---|
| F5-CA1 | Examina el `Set-Cookie` real del servidor embebido al crear y rotar una sesión | `sessionCookieIsSecureAndLoginRotatesTheSessionIdWithoutUrlRewriting` | PASA |
| F5-CA2 | Compara el ID previo al login con el ID autenticado | `loginRotatesSessionIdAndStampsActivityTimes`; prueba HTTP de Fase 5 | PASA |
| F5-CA3 | Avanza un `Clock` simulado cinco minutos sin actividad | `expiresApiSessionAfterFiveMinutesOfInactivity` | PASA |
| F5-CA4 | Hace sondeos durante diez minutos simulados y comprueba que no desplazan la expiración; una petición interactiva sí actualiza actividad | `continuousPollingStillExpiresAfterTheIdleLimit`, `pollingDoesNotRefreshActivityButAnInteractiveRequestDoes` | PASA |
| F5-CA5 | Avanza el reloj 120 minutos con actividad reciente | `absoluteTimeoutExpiresDespiteRecentActivity` | PASA |
| F5-CA6 | Inicia sesión dos veces y comprueba el rechazo JSON de la primera sesión y continuidad de la segunda | `secondLoginReplacesThePreviousSession` | PASA |
| F5-CA7 | Suspende al usuario tras el login y comprueba que la petición siguiente queda denegada y auditada | `suspendedUserSessionIsRejectedOnItsNextRequest` | PASA |
| F5-CA8 | Comprueba cookie-only tracking y que HTML/redirecciones no incluyan `jsessionid` | `sessionCookieIsSecureAndLoginRotatesTheSessionIdWithoutUrlRewriting` | PASA |

## 3. Pruebas

Comando, ejecutado desde `hexora/`:

```powershell
.\mvnw.cmd -q "-Dmaven.compiler.useIncrementalCompilation=false" test
```

Resultado: **110 pruebas, 0 fallos, 0 errores y 1 omitida; código de salida 0**. Las pruebas que requieren base de datos se ejecutan con MySQL desechable.

## 4. Comportamiento al expirar

- Web: redirección a `/login?expired`; un reemplazo por login concurrente redirige a `/login?replaced`.
- API: HTTP 401 con `error: SESSION_EXPIRED` o `error: SESSION_REPLACED`.
- El JavaScript global redirige al login y conserva el motivo de reemplazo para mostrar un aviso.
- Los metadatos de sesión ausentes o inválidos también provocan expiración; no se reconstruyen con valores por defecto.

## 5. Alcance y seguridad operacional

- No se creó ni aplicó ninguna migración y no se realizaron escrituras a la BD compartida.
- El timeout de sesión del contenedor está fijado en 10 minutos como respaldo; los límites de inactividad y duración absoluta se aplican de forma independiente mediante un `Clock` inyectable.
- La verificación de estado del principal consulta la BD en cada solicitud autenticada, para que suspensiones y cambios de rol/organización se apliquen inmediatamente.
- La expiración de una sesión HTTP cierra conexiones WebSocket asociadas a esa sesión.
- No se cambió `main` ni se inició la Fase 6.

## 6. Estado

Implementación y regresión automatizada completadas. La Fase 5 queda lista para revisión y aprobación; detener el trabajo aquí hasta recibir autorización para avanzar.
