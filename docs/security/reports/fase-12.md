# Fase 12 — Auditoría y registros

## 1. Estado

**Implementación completa; pendiente de aprobación.** La persona autorizó iniciar esta fase después de dos trabajos previos: validar las herramientas por el puerto nuevo y sembrar los cimientos del módulo de informes. Ambos están incluidos en este informe. La Fase 11 sigue sin aprobación escrita; su pendiente de PDF no se resuelve aquí, pero los cimientos de esta entrega lo preparan.

Respaldo previo a todos los cambios de esta sesión: `C:\Users\Fredy\OneDrive\Documentos\dumps\fase-12\20261111_214942\` (árbol de trabajo, volcado de `hexora_db` en V27, `.env` y sumas SHA-256).

## 2. Trabajo previo pedido por la persona

### 2.1 Herramientas, puerto nuevo y base de datos

- La única base del servidor es `hexora_db`. No existe `hexora_BD`; MySQL en Linux distingue mayúsculas en nombres de base, así que no se creó otra.
- Estado de la base al terminar: **V28**, 29 migraciones validadas, todas con éxito.
- La aplicación corre en `https://localhost:8443` con el usuario de ejecución `hexora_app` y Flyway con `admin1`.
- `src/test/js/fase12-herramientas.mjs` recorre cada herramienta contra la aplicación real y la base compartida: acceso, registro, MFA, proyectos, áreas, hallazgos con riesgo calculado, búsqueda CVE, páginas y API del módulo de red, rangos autorizados, rechazo de escaneo sin rango, WebSocket, panel de informes y cierre de sesión. Resultado en §5.
- Las tareas de VS Code (`.vscode/tasks.json`) contienen rutas de otro equipo (`C:\Users\Luis David\...`). No se modificaron por ser configuración compartida del equipo. La configuración de lanzamiento (`launch.json`) sí funciona con el puerto 8443 porque lee `hexora/.env`.
- `docker-compose.yml` ahora separa las cuentas: `MYSQL_USER` es la cuenta de migración y un script de inicio crea la cuenta de ejecución solo con DML. Se verificó con un contenedor desechable; el contenedor real y su volumen no se tocaron.

### 2.2 Cimientos del módulo de informes

Detalle en [informes-cimientos.md](../informes-cimientos.md). Resumen:

- **Migración V28:** `informe` gana título, audiencia, formato, versión de esquema, contenido JSON, fecha y autor de creación, con restricciones de tipo, audiencia, formato y JSON válido. Un informe publicado debe tener contenido.
- **Snapshot fijo:** al crear el borrador se guarda el contenido (proyecto, organización, rangos autorizados, hallazgos con CVE y riesgo CIA, planes de mitigación, resumen y matriz de riesgo) con su SHA-256. Publicar, ver y descargar verifican el hash.
- **Cuatro tipos previstos:** informe técnico PDF, informe ejecutivo PDF en lenguaje formal no técnico, recopilatorio de planes de mitigación PDF y matriz de riesgo en Excel (`.xlsx`).
- **Conexión con el rol auditor:** API para crear, listar, ver, publicar, despublicar y descargar; panel "Informes" en el detalle del proyecto; el cliente solo ve publicados de su organización.
- **Generadores:** la interfaz `ReportRenderer` queda lista. Sin implementación, la descarga responde 501 con un código explícito. Elegir el motor de PDF y la librería de Excel es lo que sigue.

## 3. Implementación de la Fase 12

| Tarea del plan | Implementación |
|---|---|
| 1. Eventos completos | Catálogo `AuditEventType`. Se agregaron los que faltaban: `MFA_OK`, `MFA_FAIL`, `MFA_ENROLLED`, `THROTTLED`, `SESSION_REPLACED`, `USER_SUSPENDED`, `ROLE_CHANGED`, `PASSWORD_CHANGED`, `CAPABILITY_CHANGED`, `REPORT_*` y `STARTUP_CONFIG`. `OAUTH_LINKED` queda reservado: la Fase 9 se descartó. |
| 2. Solo inserción | Repositorio sin borrar ni actualizar, entidad inmutable, permisos de BD por tabla. |
| 3. Higiene de logs | Enmascarado de credenciales, tokens, sesiones y códigos en consola y archivo rotativo, incluidas las excepciones. |
| 4. Enumeración | `EnumerationDetector`: WARN al superar el umbral de 404 con ámbito por usuario y minuto. |
| 5. Retención | Parámetro `tinyhat.security.audit.retention-days`. Sin borrado automático. |

Comandos nuevos del propietario, necesarios para que `USER_SUSPENDED` y `CAPABILITY_CHANGED` tengan una ruta real de emisión: suspender y reactivar usuario, y cambiar capacidades de una cuenta. Detalle en [auditoria.md](../auditoria.md).

## 4. Criterios de aceptación

| ID | Cómo se verifica | Prueba | Resultado |
|---|---|---|---|
| F12-CA1 | Una prueba por evento confirma una fila con usuario, cuenta, IP y `correlation_id` cuando aplica | `PhaseTwelveAuditIntegrationTest` (login, MFA, bloqueo, límite, denegado, 404 con ámbito, sesión reemplazada y expirada, suspensión y cambio de rol detectados, registro, verificación, recuperación, invitación, comandos del propietario, arranque); `NetworkAuditEventsIntegrationTest` (rango y escaneo); `ReportFoundationIntegrationTest` (informes); `NetworkMonitoringWebTest` (escaneo rechazado) | PASA |
| F12-CA2 | Tras un flujo completo, los archivos de log no contienen contraseña, TOTP, sesión ni tokens | `PhaseTwelveAuditIntegrationTest.logFilesNeverContain...`, `LogMaskingConverterTest` | PASA |
| F12-CA3 | `UPDATE` o `DELETE` sobre `audit_event` imposible por API | `AuditAppendOnlyTest` (4 pruebas) y comprobación en la base real: `UPDATE` y `DELETE` como `hexora_app` responden `ERROR 1142` | PASA |
| F12-CA4 | Superar el umbral de enumeración genera advertencia | `EnumerationDetectorTest`, `PhaseTwelveAuditIntegrationTest.deniedAccess...` | PASA |

Cruce entre catálogo y emisores: `everyRequiredEventHasAnEmitterAndReservedEventsHaveNone` comprueba que cada evento exigido por el plan tiene un emisor en el código, salvo el reservado.

## 5. Validación ejecutada

```powershell
cd hexora
.\mvnw.cmd -B verify
```

Resultado: **248 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS.**

Verificación con la aplicación real (HTTPS en 8443, base `hexora_db` en V28, usuario de ejecución `hexora_app`): `src/test/js/fase12-herramientas.mjs`, **24 comprobaciones, 0 fallidas, 0 errores en el log de la aplicación**.

| Herramienta | Comprobación |
|---|---|
| Acceso | Login por TLS, registro, verificación, login y TOTP |
| Proyectos | Crear proyecto, listar, crear área, crear hallazgo con riesgo calculado en servidor (nivel Crítico), HTML escapado |
| Búsqueda CVE | Responde por la API y valida la entrada (400 ante `<script>`) |
| Módulo de red | Páginas de monitoreo e historial, API de proyectos y de rangos autorizados, escaneo sin rango rechazado (422, sin escanear), WebSocket 101 |
| Informes | Panel visible, script externo servido, borrador con snapshot y hash, snapshot con hallazgo, CVE, mitigación y matriz, publicar y despublicar, descarga 501 explícita, informe ajeno 404 |
| Cierre | Logout |

Log real de archivo (`hexora/logs/tinyhat.log`) tras la ejecución: cero apariciones de contraseña, cookie de sesión, enlaces de un solo uso o `[CONSOLE NOTIFIER]`, y cero `%n` literales.

Auditoría escrita por esa ejecución (`audit_event`, últimos 30 minutos): `LOGIN_OK`, `LOGOUT`, `MFA_ENROLLED`, `NOT_FOUND_SCOPED`, `RATE_LIMITED`, `REPORT_CREATED`, `REPORT_PUBLISHED`, `REPORT_UNPUBLISHED`, `REPORT_VIEWED`, `SCAN_REJECTED`, `USER_CREATED`, `USER_VERIFIED` y `STARTUP_CONFIG`. Todas las filas con usuario tienen IP y `correlation_id`.

Nota sobre el script: el TOTP recién completado cuenta como autenticación reciente, así que publicar procede. El rechazo `REAUTH_REQUIRED` para sesiones antiguas lo cubre `ReportFoundationIntegrationTest`. Ejecutarlo dos veces seguidas desde la misma IP agota el cubo `mfa` (5 por minuto); `WAIT_MS=65000` espera a que se rellene.

Permisos en la base compartida (aplicados con `database/grant-runtime-privileges.sql`):

| Operación como `hexora_app` | Resultado |
|---|---|
| `SELECT` e `INSERT` en `audit_event` | Permitido |
| `UPDATE` o `DELETE` en `audit_event` | Denegado (1142) |
| `SELECT` en `flyway_schema_history` | Denegado |
| `CREATE TABLE` | Denegado |
| `SELECT`, `INSERT`, `UPDATE`, `DELETE` en tablas de negocio | Permitido |

## 6. Hallazgos y correcciones durante la fase

- El convertidor de enmascarado debía ser un convertidor compuesto; con el tipo equivocado, logback rechazaba la configuración y los contextos de prueba no arrancaban.
- Con `%mask(%m)%n`, logback trataba `%n` como texto literal y el log salía sin saltos de línea. Se resolvió con `%mask(%m%n%ex)`, que además enmascara el texto de las excepciones. Una prueba permanente cubre el patrón.
- `LoginAttemptFilter` registraba el bloqueo de login como `RATE_LIMITED`. Ahora es `THROTTLED`, como pide el plan, y se actualizó su prueba.
- El script de permisos fallaba en silencio por falta de una base seleccionada y dejaba al usuario con permisos amplios. Se corrigió y se verificó con operaciones permitidas y denegadas.
- `ReportContentAssembler` y las pruebas encontraron que `List.indexOf(null)` lanza excepción en listas inmutables; se hizo seguro ante nulos.

## 7. Desviaciones

Registradas en `IMPLEMENTATION_LOG.md`, entrada del 2026-11-11 (Fase 12). Las principales:

- Permisos de solo inserción aplicados por tabla (no por trigger), porque crear triggers exige privilegios que la cuenta de migración no tiene con el registro binario activo.
- Equivalente a ArchUnit con reflexión y análisis de fuentes, para no añadir una dependencia que puede no soportar Java 25 o 27.
- Comandos nuevos del propietario y eventos adicionales (`USER_VERIFIED`, `USER_REACTIVATED`, `REPORT_CREATED`, `REPORT_UNPUBLISHED`).
- `OAUTH_LINKED` reservado sin emisor.

## 8. Riesgos abiertos y preguntas

1. **Permisos tras cada migración con tablas nuevas.** Hay que volver a ejecutar `grant-runtime-privileges.sql`; si no, la aplicación recibe "command denied" en la tabla nueva. V28 no crea tablas, así que hoy no hace falta.
2. **Datos ficticios acumulados en la base compartida.** Las ejecuciones de validación crearon auditores, un proyecto con área y hallazgo ficticios, y borradores de informe. Una fila `GRANT_CHECK` en `audit_event` quedó de la verificación de permisos y no se borra porque la tabla es de solo inserción.
3. **Contadores en memoria.** Limitador, bloqueo de login y detector de enumeración se pierden al reiniciar y no se comparten entre réplicas (limitación ya documentada para v1).
4. **El trabajo de Fases 10, 11 y 12 sigue sin commit** en `seguridad/fase-11`.
5. **PDF.** Falta decidir el motor de PDF, la librería de Excel y las plantillas de redacción del informe ejecutivo. Ver [informes-cimientos.md](../informes-cimientos.md).
6. **Fase 11 sin aprobación escrita.** Esta fase se inició por instrucción directa de la persona, no por un `APROBADA FASE 11`.

## 9. Pasos manuales para la persona

1. Abrir `https://localhost:8443`, iniciar sesión con un auditor y abrir un proyecto. Debe verse el panel "Informes". Crear un borrador, publicarlo (pide confirmar identidad si pasaron más de 5 minutos) y comprobar que "Descargar" explica que el generador aún no existe.
2. Auditoría reciente (con la contraseña root del `.env`):

   ```powershell
   docker exec -e MYSQL_PWD=<DB_ROOT_PASSWORD> hexora-mysql mysql -uroot -e "SELECT ts, evento, resultado, ip FROM hexora_db.audit_event ORDER BY id DESC LIMIT 15"
   ```

3. Solo inserción (debe responder `ERROR 1142`):

   ```powershell
   docker exec -it hexora-mysql mysql -uhexora_app -p -e "DELETE FROM hexora_db.audit_event WHERE id = 0"
   ```

4. Revisar que el log de archivo no contiene secretos: `hexora/logs/tinyhat.log`.

**No iniciar la Fase 13 hasta recibir `APROBADA FASE 12`.**
