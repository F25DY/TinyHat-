# TinyHat — Runbook de demostración de la capa de seguridad

Guion para mostrar al jurado que la capa de seguridad v1 funciona. Dura unos 20 minutos.

> **Estado de este guion.** Lo escribió el agente a partir del código y de las pruebas. **El agente no lo ejecutó de punta a punta**: no abre un navegador ni usa tu autenticador. F14-CA4 exige que **tú** lo recorras una vez completo y anotes cualquier paso que falte. Los nombres exactos de botones pueden variar; las rutas y los resultados esperados sí están verificados por pruebas, que se citan en cada escena.

## 0. Reglas de la demo

1. **Solo red de laboratorio.** El descubrimiento y el escaneo de puertos generan tráfico real. Usa un router propio, el punto de acceso de un móvil o máquinas virtuales aisladas. **Nunca** una red de la universidad, de una empresa o de tu casa compartida con terceros.
2. **Solo datos ficticios** (regla R9). Nombres de organización y proyecto inventados. Nada de clientes ni auditorías reales.
3. **Aplicación enlazada a `127.0.0.1`.** Solo se alcanza desde el mismo equipo. No cambies `server.address`.
4. **Haz un respaldo antes** y restáuralo al terminar (§6): la demo crea cuentas, proyectos y filas de auditoría en la base.

## 1. Preparación (una vez, antes de la demo)

| Requisito | Cómo comprobarlo |
|---|---|
| Docker Desktop encendido | `docker --version` |
| MySQL del proyecto arriba | `cd hexora; docker compose up -d` y luego `docker ps` muestra `hexora-mysql` (puerto 3307) |
| `.env` completo | Compara con `hexora/.env.example`. Obligatorias: `DB_USER`, `DB_PASSWORD`, `FLYWAY_DB_USER`, `FLYWAY_DB_PASSWORD`, `TINYHAT_MFA_KEY` (32 bytes en Base64) |
| Permisos de la cuenta de ejecución | Si migraste a una versión nueva, repite `hexora/database/grant-runtime-privileges.sql` (ver `db-privilegios.md`) |
| Certificado HTTPS local | Sigue `https-local.md` hasta el paso 3: `local-dev.p12`, `localhost.crt` y estas variables en `.env`: `TINYHAT_SSL_ENABLED=true`, `TINYHAT_SSL_KEY_STORE=file:./local-dev.p12`, `TINYHAT_SSL_KEY_STORE_PASSWORD`, `APP_PORT=8443`, `PUBLIC_BASE_URL=https://localhost:8443` |
| Certificado confiable en el navegador | Importa `localhost.crt` (paso 5 de `https-local.md`). Sin eso el navegador avisa y la CSP no se ve limpia |
| Aplicación autenticadora | Una en el móvil (Google Authenticator, Aegis, etc.). Para dos cuentas, dos entradas |
| Dos navegadores o dos perfiles | Uno para el auditor A y otro para el auditor B |

**No hay sembrador de datos.** Los perfiles tienen `tinyhat.demo.seed.enabled=false` y no existe código que siembre datos por perfil (`application-dev.properties` lo dice). Los datos de la demo se crean a mano en la escena 1. Si quieres una demo con datos precargados, hay que construir ese sembrador: es trabajo nuevo que no pidió el plan.

Respaldo previo:

```powershell
docker exec hexora-mysql sh -c 'mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" hexora_db' > "$HOME\dumps\demo-previo.sql"
```

## 2. Arranque

```powershell
cd hexora
$env:SPRING_PROFILES_ACTIVE = "demo"
.\mvnw.cmd spring-boot:run
```

Comprueba, en otra terminal:

```powershell
curl.exe --cacert localhost.crt -sS -o NUL -D - https://localhost:8443/login
```

Debe responder `200` e incluir `Content-Security-Policy` (sin `-Report-Only`), `Strict-Transport-Security`, `Cross-Origin-Opener-Policy`, `Permissions-Policy` y `Cache-Control: no-store`. No uses `curl -k`.

En el arranque, la aplicación escribe una fila `STARTUP_CONFIG` en la auditoría con la configuración efectiva **sin secretos**. Los enlaces de verificación e invitación aparecen en la **consola**, no en un correo (`ConsoleNotifier`, solo perfiles `dev` y `demo`).

## 3. Escenas

### Escena 1 — Alta de dos auditores y segundo factor (TOTP)

*Muestra: registro sin rol, verificación, TOTP obligatorio.*

1. Navegador A: `https://localhost:8443/registro`. Registra `auditor.a@demo.tinyhat.local` con una contraseña de 12 caracteres o más.
2. La respuesta es neutra. Copia el enlace `/verificar/<token>` que imprime la consola y ábrelo. El token sirve **una sola vez**.
3. Inicia sesión. **No llegas al panel:** el sistema te lleva a `/mfa/enroll`. Escanea el código QR con el autenticador y confirma el primer código.
4. Guarda los 10 códigos de recuperación (se muestran **una vez**).
5. Repite en el navegador B con `auditor.b@demo.tinyhat.local`.

Qué decir: la cuenta nueva es siempre `AUDITOR`; el formulario no tiene campo de rol y, aunque se manipule, el servidor lo ignora. Antes del TOTP el usuario solo tiene `ROLE_PRE_MFA` y cualquier ruta de negocio lo rechaza.

Respaldo en pruebas: `MfaFlowIntegrationTest.enrollmentEncryptsTheSecretGatesBusinessRoutesAndRotatesSessionAfterTotp` (F7-CA1, CA2), `PhaseEightRegistrationSecurityIntegrationTest.f8ca1_registrationIgnoresSubmittedRoleAndAccountId` (F8-CA1).

### Escena 2 — Aislamiento entre cuentas: cambiar un ID da 404

*Muestra: decisión 3, "recurso ajeno ⇒ 404".*

1. Auditor A: crea una organización y un proyecto ficticios desde `/proyectos/nuproyectos` (por ejemplo "Organización Demo A" y "Proyecto Demo A"). Abre el proyecto y **copia la URL**: `https://localhost:8443/proyectos/<uuid>`.
2. Auditor B: pega esa URL.
3. Resultado esperado: **404**, idéntico al de un UUID inventado. B no puede distinguir "no existe" de "es de otro".
4. Prueba también un UUID inventado y compara las dos respuestas.
5. Muestra la huella en la auditoría:

```powershell
docker exec -it hexora-mysql mysql -u root -p hexora_db -e "SELECT ts, evento, usuario_id, cuenta_id, resultado FROM audit_event WHERE evento = 'NOT_FOUND_SCOPED' ORDER BY ts DESC LIMIT 5;"
```

Qué decir: los IDs son UUID, no números que se puedan recorrer; el filtro por cuenta sale del usuario autenticado, nunca de la petición; y aun si el código fallara, la base de datos tiene claves foráneas compuestas que impiden enlazar filas de cuentas distintas.

Respaldo en pruebas: `PhaseThreeSecurityIntegrationTest.scopedResourceNotFoundIsAGeneric404AndNotAServerError`, `ReportFoundationIntegrationTest.auditorsOfOtherAccountsAndUnassignedAuditorsOfTheSameAccountGetANotFound`, `PhaseTwoMigrationIntegrationTest.rejectsNetworkScanReferencingHostFromAnotherAccount`, `PhaseTwelveAuditIntegrationTest.deniedAccessScopedNotFoundAndEnumerationWarningAreRecorded`.

> La matriz completa entre dos cuentas sobre las 21 rutas de proyecto, área y hallazgo sí está automatizada (`CrossAccountIsolationIntegrationTest`, 95 peticiones). Esta escena es la versión que se ve en pantalla.

### Escena 3 — Escaneo fuera de alcance: rechazo antes de tocar la red

*Muestra: decisión 9, `ScopeGuard`.*

1. Auditor A: abre `/network-monitoring`, elige "Proyecto Demo A" y pulsa el escaneo **sin haber declarado un rango**.
2. Resultado esperado: **rechazo** con motivo; no se envía ningún paquete.
3. Declara un rango autorizado que contenga la red de laboratorio (por ejemplo `192.168.1.0/24`) con una referencia ficticia: "DEMO-LAB-001, red propia del equipo".
4. Intenta declarar un rango público (por ejemplo `8.8.8.0/24`) y uno mayor que /24 (por ejemplo `10.0.0.0/16`). Resultado esperado: **rechazo**, al declararlo o al intentar escanear (`allow-public-ranges=false`, `max-prefix=24`).
5. Con el rango correcto y la interfaz del equipo dentro de él, el escaneo procede. Un segundo escaneo inmediato del mismo proyecto puede recibir 429 por el enfriamiento de 60 s y el bloqueo de un escaneo activo por proyecto.
6. Muestra `SCAN_REJECTED`, `RANGE_DECLARED` y `SCAN_STARTED` en `audit_event`.

Qué decir: el consentimiento queda declarado y auditado, no supuesto. La aplicación comprueba que el rango sea privado, no mayor que /24 y que contenga la interfaz activa; no puede comprobar que tengas autorización legal real, y por eso la referencia queda guardada con tu nombre y la fecha.

Respaldo en pruebas: `NetworkScopeGuardTest` (7), `NetworkMonitoringWebTest.rejectsAProjectScanWithoutAnAuthorizedRangeAndAuditsTheRejectedTarget`, `.rejectsAnOversizedRangeBeforeStartingAScan`, `NetworkMonitoringWebTest.allowsOnlyOneConcurrentCooldownGrantAndReopensAfterExpiry`. Ninguna prueba genera tráfico real.

### Escena 4 — Sesión: una sola, y que caduca

*Muestra: decisión 4.*

**Sesión única.** Inicia sesión con A en un tercer navegador (o ventana privada). El primer navegador, en su siguiente petición, vuelve a `/login?replaced`. Muestra `SESSION_REPLACED` en la auditoría.

**Caducidad por inactividad.** El límite real es de 5 minutos. Para no esperar, arranca con un valor corto:

```powershell
$env:TINYHAT_SECURITY_SESSION_IDLE_MINUTES = "1"
```

Deja la sesión **en una página que no consulte datos por sí sola**, como `/proyectos`. Tras el minuto, cualquier clic lleva a `/login?expired`.

> **Pantalla de red.** Las consultas periódicas de `/network-monitoring` (`/api/network/status`, `/history` y `/discoveries/history`, cada 30 s) están en `poll-paths` y **no** cuentan como actividad; por eso la sesión también caduca con esa pantalla abierta. `SessionPollPathsConsistencyTest` vigila que siga así. Cualquier clic o acción del usuario sí reinicia el plazo.

Respaldo en pruebas: `PhaseThreeSecurityIntegrationTest.secondLoginReplacesThePreviousSession` (F5-CA6), `SessionActivityFilterTest.expiresApiSessionAfterFiveMinutesOfInactivity`, `.continuousPollingStillExpiresAfterTheIdleLimit` (F5-CA4), `.absoluteTimeoutExpiresDespiteRecentActivity`, `SessionPollPathsConsistencyTest`.

### Escena 5 — Fuerza bruta frenada

*Muestra: decisión 7.*

1. En un navegador sin sesión, escribe la contraseña **incorrecta** 5 veces seguidas para `auditor.a@demo.tinyhat.local`.
2. En el sexto intento escribe la contraseña **correcta**. Resultado esperado: sigue rechazado, con **el mismo mensaje** que un error normal, durante la ventana de bloqueo (60 s tras el quinto fallo).
3. Repite con un correo que no existe. La respuesta es la misma: no se puede averiguar qué correos están registrados.
4. Muestra `LOGIN_FAIL` y `THROTTLED` en la auditoría.

Respaldo en pruebas: `LoginAttemptFilterTest.blocksLoginDuringAccountOrIpCooldownWithGeneric429`, `LoginAttemptServiceTest`, `PhaseThreeSecurityIntegrationTest.loginFailuresHaveIdenticalResponsesAndAreAuditedWithCorrelationIds`.

> Después de esta escena, espera el desbloqueo antes de continuar con A.

### Escena 6 — Cierre: trazabilidad y calidad

1. Auditoría: `SELECT evento, COUNT(*) FROM audit_event GROUP BY evento ORDER BY 2 DESC;`
2. La aplicación no puede modificar ni borrar esas filas. Muéstralo conectándote con la **cuenta de ejecución** (`DB_USER` del `.env`, por ejemplo `hexora_app`): `UPDATE` y `DELETE` sobre `audit_event` responden `ERROR 1142`.
3. Los logs no contienen contraseñas, códigos TOTP, IDs de sesión ni tokens: `Select-String -Path logs\tinyhat.log -Pattern "password|totp|JSESSIONID|TINYHAT_SID"` no debe mostrar valores.
4. Ejecuta la suite completa: `.\mvnw.cmd -B verify` (unos 4 minutos; usa Docker).
5. Muestra `trazabilidad.md` e `inventario-final.md`.

## 4. Si algo sale mal

| Síntoma | Causa probable | Solución |
|---|---|---|
| La aplicación no arranca y habla de `TINYHAT_MFA_KEY` | Falta la clave o no es de 256 bits en Base64 | `openssl rand -base64 32` y ponla en `.env` |
| Error de conexión a la base | Docker apagado o contenedor detenido | `docker compose up -d` |
| `ERROR 1142` al arrancar, en una tabla que acaba de crearse | La cuenta de ejecución no tiene permisos sobre tablas nuevas | Repite `database/grant-runtime-privileges.sql` |
| El WebSocket no conecta | Abriste la página por un origen distinto de `https://localhost:8443` o `https://127.0.0.1:8443` | Usa uno de esos dos |
| 400 al abrir `https://localhost:8443` | HTTPS no activado pero el puerto es 8443 | Revisa `TINYHAT_SSL_ENABLED=true` |
| No llega ningún enlace | `ConsoleNotifier` imprime en la consola de Maven, no en el navegador | Mira la terminal donde corre `spring-boot:run` |
| El escaneo se rechaza siempre | La interfaz activa no está dentro del rango declarado | Declara el CIDR real de tu red de laboratorio |

## 5. Lo que **no** debes prometer

Lee `limitaciones.md` antes de la demo. En particular: no hay OAuth, no hay PDF (la descarga responde 501), no hay correo real, y el sistema corre en un solo equipo.

## 6. Al terminar

1. Detén la aplicación.
2. Restaura el respaldo si no quieres conservar las cuentas de demostración:

```powershell
Get-Content "$HOME\dumps\demo-previo.sql" | docker exec -i hexora-mysql sh -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" hexora_db'
```

3. Quita `localhost.crt` del almacén de confianza del navegador.
4. Borra las variables temporales (`Remove-Item Env:SPRING_PROFILES_ACTIVE, Env:TINYHAT_SECURITY_SESSION_IDLE_MINUTES`).
