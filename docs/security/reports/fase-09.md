# Fase 9 — OAuth2 / OIDC

## 1. Estado de la fase

**NO IMPLEMENTADA; decisión: no incorporar OAuth2/OIDC.** El plan rector define Fase 9 como inicio de sesión OAuth2/OIDC, no como entrega SMTP. Por decisión del usuario, Hexora mantendrá el inicio de sesión actual con MFA obligatorio y no añadirá este proveedor de identidad, para evitar ampliar innecesariamente la superficie de autenticación. La revisión del código, dependencias, rutas, migraciones y pruebas no encontró un cliente OAuth, un `OidcUserService`, vínculo de identidades externas, flujo `/vincular`, PKCE ni pruebas F9-CA1–F9-CA8. La propiedad `tinyhat.security.oauth.enabled=true` y la clasificación de rutas OAuth en el rate limiter no constituyen una implementación funcional.

La implementación SMTP que existe en el árbol de trabajo es una adición independiente iniciada al interpretar erróneamente el alcance de Fase 9. Se conserva para no descartar trabajo, pero no cuenta como avance ni evidencia de OAuth. Esta desviación se registra en [`IMPLEMENTATION_LOG.md`](../IMPLEMENTATION_LOG.md).

## 2. Revisión de las fases 0–8

La siguiente evaluación contrasta los informes actuales con el documento rector adjunto. “Implementada” no significa que se hayan satisfecho todos los criterios originales cuando el informe registra excepciones o faltan artefactos de aceptación.

| Fase | Resultado de revisión | Evidencia y salvedades |
|---|---|---|
| 0 — Inventario y línea base | **Cierre documental incompleto** | No se encontró `docs/security/00-inventario.md` ni un informe F0. El informe de Fase 1 registra que Flyway estaba en V16, aunque el criterio F0 esperaba V17. Más tarde, la prueba de Fase 2 sí migra un MySQL desechable desde V17. |
| 1 — Configuración segura | **Implementada con evidencia reportada** | [`hexora/docs/security/reports/fase-01.md`](../../../hexora/docs/security/reports/fase-01.md) informa sus criterios y 78 pruebas sin fallos; deja documentados el sembrado heredado en V2 y la falta de escaneo Gitleaks/historial. |
| 2 — Modelo de datos | **Implementada con desviaciones documentadas** | [`hexora/docs/security/reports/fase-02.md`](../../../hexora/docs/security/reports/fase-02.md) e `PhaseTwoMigrationIntegrationTest` reportan V18–V23 y migración desde V17 en MySQL desechable. La secuencia fue luego desplazada por V24 (normalización de puertos); V24–V26 se asignaron a red, MFA y tokens. |
| 3 — Núcleo Spring Security | **Implementada y revalidada** | [`fase-03.md`](./fase-03.md) registra F3-CA1–CA8. La suite completa ejecutada en esta sesión vuelve a pasar las 10 pruebas de `PhaseThreeSecurityIntegrationTest`. |
| 4 — Aislamiento | **Implementación sustancial; conformidad integral no demostrada** | [`fase-04.md`](./fase-04.md) registra pruebas de aislamiento, scheduler y WebSocket. El mismo informe admite que las rutas de red conservan IDs numéricos y que no existe flujo de informes expuesto. Su matriz no acredita todos los nueve criterios del documento rector (por ejemplo, cobertura integral de endpoints, regla ArchUnit y auditoría de cada 404 con ámbito). |
| 5 — Sesión | **Implementada y revalidada** | [`fase-05.md`](./fase-05.md) reporta F5-CA1–CA8; la suite completa incluye y pasa las integraciones de sesión. La revocación persistente entre instancias se añadió después y quedó probada en Fase 8. |
| 6 — Intentos y rate limit | **Implementada con una verificación externa pendiente; F6-CA5 resuelto en Fase 7** | [`fase-06.md`](./fase-06.md) informa pruebas de intentos, IP, buckets y NVD. F6-CA5 estaba pendiente al redactarse ese informe y quedó cubierto por el flujo TOTP/F7-CA4. La cuota oficial de NVD no fue verificable y sigue siendo una salvedad documental. |
| 7 — MFA/TOTP | **Implementada con alcance explícito** | [`fase-07.md`](./fase-07.md) reporta F7-CA1–CA9 y la migración V25 en MySQL desechable. F7-CA8 se limita a la acción crítica disponible entonces (reinicio MFA); las demás acciones críticas deben verificarse en sus fases correspondientes. |
| 8 — Registro, invitaciones y recuperación | **Implementada y revalidada; numeración de migración desviada** | [`fase-08.md`](./fase-08.md) reporta F8-CA1–CA10; las 9 integraciones de Fase 8 y la suite completa pasan. V26 contiene tokens y el marcador de revocación, no `identidad_externa`. No se tocó la base compartida. |

La última ejecución de suite registrada en esta rama fue **143 pruebas, 0 fallos, 0 errores y 1 omitida; BUILD SUCCESS**. Es una regresión del estado actual, no una prueba retroactiva de cada requisito manual ni de las pruebas/condiciones de todos los hitos históricos.

## 3. Criterios de aceptación de Fase 9

| ID | Verificación requerida por el plan | Estado actual |
|---|---|---|
| F9-CA1 | `email_verified=false` rechaza el login | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA2 | Claims `roles`/`authorities` con `ADMIN` no elevan permisos | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA3 | Mismo correo con otro `sub` no accede a una cuenta existente | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA4 | Correo coincidente requiere `/vincular` con contraseña antes de crear vínculo | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA5 | Login OAuth queda `PRE_MFA` y rota el ID de sesión | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA6 | Redirección contiene PKCE (`code_challenge`), `state` y `nonce` OIDC | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA7 | No se guardan secretos ni tokens del proveedor en BD/logs | **NO IMPLEMENTADO / NO VERIFICABLE** |
| F9-CA8 | `oauth.enabled=false` hace que la ruta de autorización responda 404 | **NO IMPLEMENTADO / NO VERIFICABLE** |

No se ejecutó una suite OAuth porque no existe implementación ni pruebas para esos criterios. No se conectó con proveedores externos.

## 4. Migraciones y discrepancia de numeración

El plan reserva V26 para `identidad_externa`, pero esa versión ya existe como `V26__create_security_tokens.sql` de Fase 8, con tokens de seguridad y revocación persistente. No se debe editar ni reutilizar una migración existente.

La secuencia efectiva ya se apartó del plan: V24 normaliza puertos, V25 crea MFA y V26 crea tokens. No se creó ni aplicó migración OAuth en esta revisión. V27 se asignó posteriormente a Fase 10; si en el futuro se reevalúa la decisión de no añadir OAuth, deberá revisarse el mapa de migraciones y usarse la siguiente versión libre.

## 5. Desviaciones registradas

- La implementación SMTP en el árbol de trabajo se conserva como trabajo complementario, no como Fase 9 ni como cumplimiento de un criterio OAuth. Las pruebas focalizadas actuales verifican composición, propagación de fallos y guardas de configuración de producción; no verifican entrega real por relay.
- El mapa de migraciones del documento no coincide con la secuencia aplicada/probada posteriormente. V27 ya está asignada a Fase 10; cualquier cambio futuro de identidad externa debe mantener el historial y usar una nueva versión de migración.
- El inventario F0 requerido no aparece en el repositorio. Los informes de Fases 1–8 contienen salvedades propias, detalladas en la tabla de revisión; no se declara que estén aprobadas retroactivamente por esta auditoría.

## 6. Resultado y siguiente paso

La revisión **no puede confirmar que todas las fases 0–9 estén completas con éxito**: Fase 0 carece del artefacto exigido, Fase 4 conserva brechas reconocidas, Fase 6 tiene pendiente la confirmación documental de la cuota NVD y Fase 9 (OAuth2/OIDC) no está implementada.

No iniciar OAuth/OIDC: la decisión vigente es no incorporarlo. Si esa decisión cambia, deberá abrirse una revisión de seguridad y alcance antes de implementar o migrar. Los secretos/credenciales de cualquier proveedor deben permanecer fuera del repositorio.
