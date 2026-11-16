# Fase 6 — Limitador de intentos y rate limit

## 1. Resumen

Se añadieron límites por IP y por usuario para las clases de endpoints de la hoja de ruta, un control temporal de intentos de inicio de sesión y un presupuesto común de salida hacia NVD. La Fase 5 permanece sin cambios y la rama de trabajo continúa siendo `seguridad/fase-01`.

- `IpRateLimitFilter` se ejecuta temprano en las cadenas web y API. Resuelve la IP desde `getRemoteAddr()` y solo recorre `X-Forwarded-For` cuando el proxy directo pertenece a `tinyhat.security.trusted-proxies`.
- `UserRateLimitFilter` consume un cubo independiente por usuario y clase de endpoint después de establecer el principal autenticado.
- Los rechazos de cubo devuelven HTTP 429, `Retry-After` y `RATE_LIMITED`. La auditoría se agrupa por sujeto/categoría durante un minuto.
- `LoginAttemptService` cuenta por email normalizado —exista o no— y por IP. Aplica la secuencia configurable `0,0,0,0,60,300,900,1800` segundos con ventana de reinicio de 30 minutos. El éxito limpia el contador del email, no el contador compartido de IP.
- Un inicio de sesión bloqueado conserva la respuesta genérica habitual (`302` a `/login?error`), incluso si el cubo de autenticación por IP ya se agotó. La limitación ordinaria del cubo sigue devolviendo 429.
- `CveService` consulta primero el catálogo local y ahora comparte un bucket de NVD entre búsquedas y consultas de detalle.
- Los límites de tamaño de cabeceras, formularios, multipart y timeout de conexión del contenedor quedaron configurados.

## 2. Criterios de aceptación

| ID | Verificación | Prueba / evidencia | Resultado |
|---|---|---|---|
| F6-CA1 | Tras el quinto fallo, un intento posterior —también con contraseña correcta— no llega al proveedor y conserva la redirección genérica de un fallo normal | `LoginAttemptFilterTest`, `IpRateLimitFilterTest` y regresión de login de Fase 3 | PASA |
| F6-CA2 | El contador y la misma respuesta no dependen de que el email exista | `LoginAttemptServiceTest`; la integración de login verifica mensajes genéricos iguales para usuario inexistente y contraseña incorrecta | PASA |
| F6-CA3 | El estado de IP se comparte entre emails distintos | `LoginAttemptServiceTest` comprueba que un segundo email queda bloqueado por los fallos acumulados de la IP | PASA |
| F6-CA4 | Agotar el cubo produce 429 con `Retry-After`; el bucket acepta de nuevo al completar el intervalo de reposición | `IpRateLimitFilterTest`, `UserRateLimitFilterTest`, `RateLimitServiceTest` | PASA |
| F6-CA5 | Cinco TOTP incorrectos terminan el paso de inicio de sesión | No verificable todavía: esta rama no tiene flujo ni endpoint TOTP. Se limita la clase `/mfa/**`; el contador de TOTP y la terminación de su paso corresponden al flujo funcional de Fase 7 | PENDIENTE DE FASE 7 |
| F6-CA6 | Un `X-Forwarded-For` falsificado no altera la IP cuando el peer directo no es de confianza | `ClientIpResolverTest`; también cubre una cadena de proxies CIDR confiables | PASA |
| F6-CA7 | Una ráfaga de búsquedas no supera el presupuesto NVD configurado | `CveServiceTest` usa un servidor HTTP local simulado y comprueba que solo salen dos solicitudes con un presupuesto de dos | PASA |
| F6-CA8 | Los recursos estáticos no consumen fichas | `IpRateLimitFilterTest` | PASA |

El proveedor `DaoAuthenticationProvider` existente realiza la comparación con un hash señuelo cuando no encuentra el usuario; se conserva ese comportamiento para reducir diferencias de tiempo entre emails existentes e inexistentes.

## 3. Configuración y dependencias

- Bucket4j `com.bucket4j:bucket4j_jdk17-core:8.19.0` y Caffeine `3.3.1`; ambas son versiones release, sin `SNAPSHOT`. Se usa el artefacto `-core` —el nombre `bucket4j_jdk17` corresponde a un POM agregador, no al JAR—.
- Los presupuestos de NVD se configuran en `tinyhat.nvd.*`; las variables `NVD_REQUESTS_WITHOUT_KEY`, `NVD_REQUESTS_WITH_KEY`, `NVD_RATE_WINDOW_SECONDS` y `NVD_API_KEY` permiten ajustarlos por entorno. Valores iniciales: 4 solicitudes/30 s sin clave y 45/30 s con clave.
- Se intentó consultar la documentación oficial vigente de NVD, pero su página entregó únicamente la carcasa de una aplicación SPA y no permitió verificar las cuotas publicadas. Los valores anteriores son límites conservadores configurables, no una confirmación de las cuotas oficiales.
- `tinyhat.security.trusted-proxies` queda vacío por defecto. En producción detrás de un proxy debe configurarse con sus IP o CIDR reales; si no, se ignoran deliberadamente las cabeceras reenviadas.
- Los buckets de rate limit, los intentos de login y el limitador NVD residen en memoria de cada proceso. No coordinan entre réplicas ni sobreviven a un reinicio; antes de escalar horizontalmente se requerirá almacenamiento/limitación compartidos y un presupuesto NVD coordinado entre réplicas.

## 4. Alcance pendiente

- No hay aún un flujo MFA/TOTP, callback OAuth funcional, invitación, verificación de correo ni emisión/validación de tokens en esta rama. Se clasifican las rutas previstas para aplicar sus cubos al incorporar esos flujos; no se afirma que sus endpoints inexistentes ya estén protegidos. En particular, F6-CA5 requiere que Fase 7 implemente el paso TOTP.
- La cuota NVD no se verificó contra documentación oficial legible en esta sesión.
- No se creó ni aplicó ninguna migración y no se ejecutó la aplicación contra la BD compartida. Las pruebas de integración usan bases MySQL desechables.

## 5. Pruebas

Comando, ejecutado desde `hexora/`:

```powershell
.\mvnw.cmd -q "-Dmaven.compiler.useIncrementalCompilation=false" test
```

Resultado: **122 pruebas, 0 fallos, 0 errores y 1 omitida; código de salida 0**.

## 6. Estado

La implementación y la regresión están listas para revisión. F6-CA5 y la verificación documental de cuotas NVD quedan expresamente pendientes según el alcance descrito; detenerse aquí y no iniciar la Fase 7 sin aprobación.
