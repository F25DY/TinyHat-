# Fase 8 — Registro, verificación, invitaciones y recuperación

## 1. Estado

La implementación de aplicación de Fase 8 está completada en la rama `seguridad/fase-08`. Las pruebas de integración pasaron con MySQL 8.0.46 desechable en Testcontainers y Flyway validó/aplicó las 26 migraciones hasta V26. La suite completa también pasó. **No se ejecutó ninguna migración contra la BD compartida.**

La numeración se ajustó al historial real: V24 ya se ocupó en la normalización de puertos, V25 contiene MFA y Fase 8 usa V26. El documento rector asignaba V26 a identidades OAuth, pero se decidió no incorporar OAuth/OIDC. V26 no debe reutilizarse; V27 se asignó posteriormente a Fase 10 para rangos autorizados de red. No ejecutar V27 en la BD compartida sin respaldo, aprobación y ventana de migración.

## 2. Implementación

- Registro propio crea una cuenta AUDITOR, habilita las capacidades iniciales, crea un usuario `AUDITOR` en estado `PENDIENTE_VERIFICACION` y envía un enlace de verificación.
- Los campos no reconocidos como `rol` o `cuentaId` no asignan permisos ni cuentas. El email se normaliza antes de consultar/guardar.
- La respuesta JSON de registro conserva el mismo mensaje para emails nuevos y existentes; al email existente se le envía un aviso neutro.
- La política valida mínimo 12 caracteres, máximo 72 bytes UTF-8, contraseñas comunes y coincidencia con el email.
- Los tokens se generan con 32 bytes aleatorios, se guarda SHA-256 hexadecimal y se consumen mediante una actualización atómica filtrada por hash, tipo y expiración.
- Las invitaciones solo se crean para organizaciones visibles dentro de la cuenta del auditor. El rol `CLIENTE_ADMIN` y la organización se derivan en el servidor; el invitado fija su contraseña y, al iniciar sesión, debe inscribir MFA.
- La recuperación responde de forma neutra. Cambiar la contraseña invalida las sesiones del usuario y sus tokens pendientes, y audita `PASSWORD_RESET`.
- Los enlaces se construyen desde `PUBLIC_BASE_URL`, no desde el encabezado `Host` de la solicitud. Producción exige que sea HTTPS.
- `ConsoleNotifier` solo se activa con perfiles `dev`/`demo`; al cierre de Fase 8 el notifier SMTP aún era un stub. Una implementación posterior de SMTP es trabajo complementario, no un criterio de aceptación de Fase 8 ni la Fase 9 OAuth2/OIDC.
- Las operaciones de propietario requieren `--tinyhat.ops.enabled=true` y `spring.main.web-application-type=none`. Suspender una cuenta registra en BD el momento de revocación de sesiones; el filtro rechaza sesiones antiguas incluso si otra instancia no las registró o la cuenta ya fue reactivada. V26 agrega `cuenta.sesiones_revocadas_en` para este control persistente.
- Se clasifican las invitaciones bajo el bucket `registro`; verificación y recuperación quedan bajo `token`.

## 3. Criterios de aceptación

| ID | Cobertura existente | Estado |
|---|---|---|
| F8-CA1 | `PhaseEightRegistrationSecurityIntegrationTest.f8ca1_registrationIgnoresSubmittedRoleAndAccountId` | PASA |
| F8-CA2 | `PhaseEightRegistrationSecurityIntegrationTest.f8ca2_registrationResponseIsIdenticalForNewAndExistingEmail` | PASA |
| F8-CA3 | `PhaseEightTokenConcurrencySecurityIntegrationTest.tokenUsedConcurrentlySucceedsOnlyOnce`; prueba unitaria adicional para evitar consumo con tipo incorrecto | PASA |
| F8-CA4 | Prueba de token vencido y ausencia del token en tablas de token/auditoría | PASA |
| F8-CA5 | Prueba de login denegado hasta verificación | PASA |
| F8-CA6 | Prueba de invitación dentro de cuenta y 404 para otra cuenta | PASA |
| F8-CA7 | Prueba de expiración de sesión después de recuperar contraseña | PASA |
| F8-CA8 | Prueba unitaria de política y prueba de endpoint para 73 bytes/lista común | PASA |
| F8-CA9 | `ProfileSecurityGuardConsoleNotifierTest` | PASA |
| F8-CA10 | Prueba de suspensión/login, expiración entre instancias y guardas del runner operativo | PASA |

## 4. Revisión de fases previas

Se revisaron el registro y los informes existentes de Fases 3–7, junto con los filtros de tenant/sesión, límites de solicitudes, MFA, la guarda de perfil y las regresiones relacionadas. Se ejecutaron las pruebas de Fase 2 (migración histórica hasta V25) y Fase 3 con Docker; también pasaron las integraciones de MFA, tenant y sesión incluidas en la suite completa.

## 5. Validación ejecutada

Integración de Fase 8 y migración V26:

```powershell
.\mvnw.cmd "-Dmaven.compiler.useIncrementalCompilation=false" "-Dtest=PhaseEightRegistrationSecurityIntegrationTest,PhaseEightTokenConcurrencySecurityIntegrationTest" test
```

Resultado: **9 pruebas, 0 fallos, 0 errores; BUILD SUCCESS**. Testcontainers inició MySQL 8.0.46 en el esquema desechable `hexora_test`; Flyway validó las 26 migraciones y aplicó V26 correctamente. La prueba concurrente de consumo de token también pasó.

Suite completa:

```powershell
.\mvnw.cmd "-Dmaven.compiler.useIncrementalCompilation=false" test
```

Resultado: **136 pruebas, 0 fallos, 0 errores y 1 omitida; BUILD SUCCESS**. La suite incluyó integraciones MySQL/Testcontainers de Fases 2–5 y 8, MFA, filtros y servicios de red.

`git diff --check` finalizó sin errores de whitespace. Se corrigieron durante esta validación dos regresiones detectadas por la suite completa: el test histórico de migración V25 configura Hibernate sin validar el esquema actual V26, y el controlador público de ciclo de cuenta declara explícitamente `@PreAuthorize("permitAll()")` para satisfacer el chequeo de seguridad por ruta.

## 6. Pendiente operacional

- Configurar `PUBLIC_BASE_URL` para el host HTTPS real antes de producción.
- La configuración SMTP se añadió después de la aprobación de Fase 8 como trabajo complementario; completar credenciales y probar entrega con el relay de producción permanece como tarea de despliegue.
- V26 solo se probó en MySQL desechable. No aplicarla a la BD compartida sin respaldo, aprobación y ventana de migración.
- La revisión correcta del alcance OAuth se registra en el [informe de Fase 9](./fase-09.md).
