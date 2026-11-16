# Fase 11 — Entradas, salidas, cabeceras, salientes y PDF

## 1. Estado

**Implementación técnica completa salvo PDF; pendiente de aprobación.** Se cerraron validación de entradas, salida HTML, CSP en modo de aplicación validada con la aplicación real sobre HTTPS, cabeceras adicionales, errores genéricos, clientes salientes endurecidos y separación de privilegios de base de datos. La decisión sobre reintentos HTTP quedó cerrada (sin reintentos).

La parte PDF (tarea 8 del plan) sigue fuera: no existe motor HTML→PDF, endpoint ni contenido de informe persistido (`Informe` solo guarda metadatos y hash). Por acuerdo con la persona, el contenido del informe publicado se definirá después de revisar esta fase y antes de iniciar la Fase 12. F11-CA4 y F11-CA8 quedan **parciales** por ese motivo.

Respaldo previo a todos los cambios de esta sesión: `C:\Users\Fredy\OneDrive\Documentos\dumps\fase-11\20261111_131323\` (árbol completo con `.git`, volcado de `hexora_db` en V27, `.env`, `README-ROLLBACK.md`, `SHA256SUMS.txt`).

## 2. Resumen de lo implementado

Archivos nuevos:

- `hexora/src/main/resources/application-dev.properties`, `application-demo.properties`, `application-prod.properties` (recreados; los originales de Fase 1 se habían perdido en un commit huérfano).
- `hexora/src/main/java/com/hexora/hexora/security/ContentSecurityPolicy.java` y `ContentSecurityPolicyTest.java`.
- `hexora/src/main/java/com/hexora/hexora/security/ApiExceptionHandler.java`, `SecureRestClientFactory.java` (sesión anterior).
- `hexora/src/test/js/fase11-validacion-https.mjs` (validación de extremo a extremo contra la app real).
- `docs/security/db-privilegios.md`; `hexora/docs/security/00-inventario.md` y `reports/fase-00.md` recuperados del historial.

Archivos modificados relevantes:

- `SecurityConfiguration`: CSP construida desde configuración (`connect-src` derivado de `tinyhat.security.ws.allowed-origins`), modo report-only gobernado por `tinyhat.security.csp.report-only`.
- `ScopedNotFoundExceptionHandler` y `NetworkApiExceptionHandler`: orden explícito para que el 404 con ámbito no sea capturado por el manejador genérico (que devolvía 500).
- `NetworkWebSocketHandshakeInterceptor`: 403 explícito al rechazar el handshake.
- `application.properties`: datasource de ejecución sin `createDatabaseIfNotExist`; URL de Flyway separada.
- `.env.example`, `hexora/docs/security/https-local.md`, `hexora/.gitignore` (`/localhost.crt`).
- Sesión anterior: Bean Validation en CVE y paginación, errores genéricos con `correlationId`, estilos inline retirados, clientes NVD/MyMemory endurecidos, exigencia de usuario Flyway distinto en `prod`.

## 3. Migraciones

Ninguna. La base compartida sigue en V27; Flyway validó las 28 migraciones al arrancar la aplicación (checksum de V27 coincide con el archivo del árbol de trabajo).

## 4. Criterios de aceptación

| ID | Cómo se verifica | Comando / prueba | Resultado |
|---|---|---|---|
| F11-CA1 | XSS en descripción CVE, hostname, nombre de proyecto y organización se muestran escapados | `node --test src/test/js/network-monitoring-security.test.js`; `NetworkMonitoringWebTest` (payload `<img>` en proyecto/organización); `cve-buscador.js` escribe con `textContent` | PASA |
| F11-CA2 | `q` inválido ⇒ 400 `VALIDATION_ERROR`; paginación > 100 ⇒ 400 | `PhaseThreeSecurityIntegrationTest.invalidCveSearchAndExcessiveHistoryLimitReturnGenericValidationErrors`; script HTTPS (comprobaciones CA2) | PASA |
| F11-CA3 | CSP en modo de aplicación y la interfaz funciona sin violaciones | Prueba MockMvc de cabecera; script HTTPS: `/login`, `/mfa/enroll`, `/dashboard`, `/proyectos`, `/proyectos/nuproyectos`, `/network-monitoring`, `/network-monitoring/history` responden 200 con CSP sin `Report-Only` y sin `<script>` inline, `<style>`, `style=`, `on*=` ni recursos externos; WebSocket real conecta (101) bajo esa CSP | PASA (automatizado); falta solo la confirmación visual de la persona en navegador (§8) |
| F11-CA4 | `<img src="http://...">` en los datos del informe no provoca petición saliente | Prueba Node de escapado en el informe web de red. Sin renderizador PDF no hay resolutor que probar | PARCIAL (PDF pendiente) |
| F11-CA5 | El cliente NVD ignora una redirección a otro host | `SecureRestClientFactoryTest` (302 hacia servidor local que nunca recibe la petición; límite de bytes) | PASA |
| F11-CA6 | Ninguna consulta concatena texto | Búsqueda de `@Query`, `nativeQuery`, `createNativeQuery`, `JdbcTemplate` en `src/main`: solo parámetros nombrados y métodos derivados | PASA |
| F11-CA7 | Errores sin traza ni nombres internos | `ApiExceptionHandler` + prueba de cuerpo sin nombre de excepción; script HTTPS: 400/401/404 con `{error, correlationId}`; log de la app sin trazas en respuestas | PASA |
| F11-CA8 | `Cache-Control: no-store` en PDF y en página de informe | Script HTTPS: `no-store` en HTML autenticado y en JSON de API. Sin endpoint PDF | PARCIAL (PDF pendiente) |
| Tarea 7 | Usuario de ejecución sin DDL; Flyway con credenciales separadas | `SHOW GRANTS FOR 'hexora_app'@'%'`; `CREATE/ALTER/DROP` como `hexora_app` ⇒ `ERROR 1142`; app arranca con `ddl-auto=validate` usando `hexora_app` y migra con `admin1`; `ProfileSecurityGuardMfaTest` rechaza usuario Flyway igual al de runtime en `prod` | PASA |
| Tarea 6 | Reintentos acotados | Decisión cerrada: sin reintentos automáticos (ver `IMPLEMENTATION_LOG.md`, 2026-11-11) | CERRADO |

## 5. Validación ejecutada

### 5.1 Aplicación real sobre HTTPS (2026-11-11)

Arranque: `java -jar target\hexora-0.0.1-SNAPSHOT.jar` desde `hexora` con `.env` (perfil `dev`, `APP_PORT=8443`, HTTPS con `local-dev.p12`). Log de arranque: `https=true, secureCookie=true, mfaRequired=true, cspReportOnly=false`; Flyway "Successfully validated 28 migrations"; Tomcat en 8443 (https).

```powershell
curl.exe --cacert localhost.crt -sS -o NUL -D - https://localhost:8443/login
```

Cabeceras observadas: `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' wss://localhost:8443 wss://127.0.0.1:8443; font-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, `Strict-Transport-Security: max-age=31536000`, `Cross-Origin-Opener-Policy: same-origin`, `Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`, cookie `TINYHAT_SID; Secure; HttpOnly; SameSite=Strict`. TLS 1.3, certificado `CN=localhost` verificado con `localhost.crt`.

```powershell
node src\test\js\fase11-validacion-https.mjs .\localhost.crt <log-de-la-app> <salida.json>
```

Resultado final: **29 comprobaciones, 0 fallidas.** La primera ejecución detectó 1 fallo real (`GET /proyectos/<uuid inexistente>` ⇒ 500), corregido con el orden de los manejadores de excepciones; la segunda ejecución pasó completa. Evidencia (JSON de ambas ejecuciones y log sin enlaces) en la carpeta de respaldo, subcarpeta `evidencia-fase-11`.

Comprobaciones del script: cabeceras en página anónima y autenticadas; CSP derivada de orígenes; registro ⇒ `302 /login?registered`; verificación por enlace de `ConsoleNotifier` ⇒ `302 /login?verified`; login ⇒ `302 /dashboard` con rotación de sesión; PRE_MFA ⇒ `302 /mfa/enroll` y API `401 MFA_REQUIRED`; inscripción TOTP (secreto de 32 caracteres base32, 10 códigos de recuperación) con nueva rotación de sesión; auditor nuevo no ve proyectos de la cuenta existente; `no-store` en JSON; `q` inválido y `limit=101` ⇒ `400 VALIDATION_ERROR`; proyecto inexistente ⇒ `404 NOT_FOUND`; WebSocket `/ws/network`: `101` con `Origin` permitido y sesión MFA, `403` con origen ajeno o sin `Origin`, `302` a login sin sesión; `POST /logout` con CSRF ⇒ `302 /login?logout`.

### 5.2 Pruebas automatizadas

- `ContentSecurityPolicyTest`, `PhaseThreeSecurityIntegrationTest` (incluye la prueba nueva de 404 con ámbito), `NetworkWebSocketHandshakeInterceptorTest` (incluye 403), `ProfileSecurityGuardMfaTest`, `ProfileSecurityGuardConsoleNotifierTest`, `SecureRestClientFactoryTest`, `NetworkApiExceptionHandlerTest`, `CveServiceTest`: ver §5.3.
- `node --check` y `node --test src/test/js/network-monitoring-security.test.js`: sesión anterior, 4 pruebas, 0 fallos.
- La suite completa destapó tres regresiones de la sesión anterior que las pruebas focalizadas no cubrían: 405 convertido en 500 por el manejador genérico, cuerpo de puertos (`MonitoredPortRequest`) rechazado antes de resolver el host (400 en lugar de 404 para hosts ajenos o inexistentes) y una prueba de Fase 5 afectada por el HTTPS del `.env` local. Las tres se corrigieron (ver `IMPLEMENTATION_LOG.md`).
- Dos pruebas de `PhaseThreeSecurityIntegrationTest` estaban desactualizadas por cambios de Fase 10 (no por esta fase): `secondLoginReplacesThePreviousSession` usaba `/api/network/status`, que ahora exige `projectPublicId` (400), y `unlistedRoutesAreDenied…` usaba `user(principal)`, cuya sesión simulada carece de marcas de tiempo y `SessionActivityFilter` la expira (302). Se ajustaron a `/api/network/projects` y a una sesión real de login; el criterio verificado (F3-CA6, F5-CA6) no cambia.

### 5.3 Resultado de la suite

```powershell
cd hexora
.\mvnw.cmd -B verify
```

Resultado: **211 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**, jar `target\hexora-0.0.1-SNAPSHOT.jar` empaquetado. Las pruebas web usan MySQL desechable de Testcontainers (Docker activo); no tocan la base compartida.

Historial de la suite en esta sesión: 1.ª ejecución con 4 fallos (405→500, cuerpo de puertos, prueba de Fase 5 afectada por HTTPS del `.env`); 2.ª ejecución con 0 fallos pero empaquetado bloqueado por la app que seguía corriendo; 3.ª ejecución con 61 errores por Docker apagado (no es fallo de código); 4.ª ejecución verde.

La validación HTTPS de 29 comprobaciones se repitió con el jar definitivo: **29 pasan, 0 fallan, 0 errores en el log** (evidencia `validacion-final-jar-definitivo.json` en la carpeta de respaldo).

Un tercer auditor ficticio `auditor.validacion.fase11.1794451141796@tinyhat.local` quedó en la BD por esa repetición (ver §7, punto 2).

## 6. Desviaciones

Registradas en `docs/security/IMPLEMENTATION_LOG.md` (entrada 2026-11-11): perfiles recreados, inventario de Fase 0 recuperado, CSP configurable, corrección de 404 ⇒ 500, 403 en WebSocket, separación de usuarios de BD, decisión de no reintentar llamadas salientes, PDF pospuesto.

## 7. Riesgos abiertos y preguntas

1. **PDF (F11-CA4, F11-CA8).** Falta definir el contenido del informe publicado (alcance y rango autorizado, hallazgos con riesgo CIA y mitigaciones, anexo de red) y su persistencia; después, motor HTML→PDF con resolutor `classpath:` únicamente, nombre de archivo del servidor, `Content-Disposition: attachment`, clase `pdf` de rate limit y solo `PUBLICADO` para CLIENTE_ADMIN.
2. **Datos ficticios en la BD compartida.** Las tres ejecuciones del script crearon los auditores `auditor.validacion.fase11.1794421321130@tinyhat.local`, `auditor.validacion.fase11.1794421482202@tinyhat.local` y `auditor.validacion.fase11.1794451141796@tinyhat.local` (cuenta, capacidades, credencial MFA, tokens, eventos de auditoría). Son ficticios; pueden conservarse para la demo o eliminarse restaurando el volcado del respaldo.
3. **Docker Compose.** Si se recrea el volumen con el `.env` actual, `MYSQL_USER` será `hexora_app` con todos los privilegios; hay que ejecutar el script de `db-privilegios.md` después.
4. **`audit_event`** aún admite `UPDATE/DELETE` para el usuario de ejecución; se restringe por tabla en Fase 12.
5. El trabajo de Fases 10 y 11 sigue **sin commit** en `seguridad/fase-11`; conviene confirmarlo antes de la revisión.

## 8. Pasos manuales para la persona

1. Importar `hexora/localhost.crt` como certificado de confianza (o aceptar la advertencia) y abrir `https://localhost:8443/login`. En las herramientas de desarrollador, pestaña Consola, no debe aparecer ningún mensaje "Refused to … because it violates the following Content Security Policy directive".
2. Iniciar sesión con un auditor (por ejemplo, registrarse en `/registro`; el enlace de verificación se imprime en la consola de la aplicación), completar TOTP y recorrer Dashboard, Proyectos, Escaneo de red e Historial. Repetir la revisión de la consola.
3. En Escaneo de red, confirmar en la pestaña Red del navegador que `wss://localhost:8443/ws/network` queda en estado `101 Switching Protocols`.
4. Cabeceras:

   ```powershell
   curl.exe --cacert localhost.crt -sS -o NUL -D - https://localhost:8443/login
   ```

5. Privilegios de BD (con la contraseña de `hexora_app` del `.env`):

   ```powershell
   docker exec -it hexora-mysql mysql -uhexora_app -p -e "CREATE TABLE hexora_db.ddl_check (id INT);"
   ```

   Debe responder `ERROR 1142 (42000): CREATE command denied`.

6. Repetir la validación automatizada cuando se quiera (crea un auditor ficticio nuevo cada vez):

   ```powershell
   node src\test\js\fase11-validacion-https.mjs .\localhost.crt <ruta-log-app> resultado.json
   ```

**No iniciar la Fase 12 hasta recibir `APROBADA FASE 11` y acordar el alcance del PDF.**
