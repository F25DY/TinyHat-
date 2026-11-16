# TinyHat — Limitaciones conocidas de la capa de seguridad v1

Este documento dice qué **no** cubre la versión 1 y por qué. Sirve para que nadie crea que la aplicación protege algo que no protege. Cada punto cita su origen en el plan rector o en `IMPLEMENTATION_LOG.md`.

## 1. Fuera de alcance por decisión del plan (R10)

| Tema | Estado |
|---|---|
| Rol ADMIN | No existe. Solo `AUDITOR` y `CLIENTE_ADMIN`. Las operaciones de propietario (suspender cuenta o usuario, cambiar capacidades, expirar sesiones) se hacen por línea de comandos, sin servidor web, con `--tinyhat.ops.enabled=true`, y quedan en auditoría. |
| Agente de red | No existe. El prefijo `/api/agent/**` está reservado y responde 401 a cualquier petición. |
| Facturación y restricciones por plan | No existen. Las capacidades por cuenta (`modulo_red`, `informes_pdf`, `monitoreo_continuo`) están todas en `true`; se consultan desde `CapabilityService`. |
| Despliegue | No hay despliegue. El perfil `prod` existe y su guarda de arranque rechaza configuraciones débiles, pero **nunca se ejecutó en un servidor real**. |
| Tratamiento legal de datos | No hay política de privacidad, consentimiento, retención legal ni supresión a petición. La retención de auditoría es un parámetro documentado, sin borrado automático. |

## 2. Funciones del plan que no se implementaron

| Tema | Estado | Origen |
|---|---|---|
| OAuth2 / OIDC (Fase 9) | **No implementado.** El usuario decidió no sumar otra frontera de autenticación. Los criterios F9-CA1 a F9-CA8 quedan sin verificar. El evento `OAUTH_LINKED` está reservado sin emisor. La decisión 5 del plan ("OAuth2 como segundo método de primer factor") queda cumplida solo en la parte de TOTP obligatorio. | `IMPLEMENTATION_LOG.md` (2026-11-09), `reports/fase-09.md` |
| Generación de PDF y Excel de informes | **No implementado.** Existen los cimientos (snapshot JSON con SHA-256, estados `BORRADOR`/`PUBLICADO`, API y panel), pero `GET /api/informes/{id}/descarga` responde 501. F11-CA4 y F11-CA8 quedan parciales: no hay resolutor de recursos del PDF que probar. | `reports/fase-11.md`, `reports/fase-12.md` |
| Correo real | `ConsoleNotifier` (dev, demo) imprime enlaces en la consola. `SmtpNotifier` (prod) tiene pruebas unitarias, pero **nunca se envió un correo real** ni se probó contra un relay. | `IMPLEMENTATION_LOG.md` (2026-11-09) |
| DAST (ZAP) | No incluido. Un escaneo base sin sesión solo recorre el login y el registro. | `reports/fase-13.md` |
| ArchUnit | Reemplazado por pruebas con reflexión y análisis de fuentes (`AuditAppendOnlyTest`) para no depender del soporte de bytecode de Java 25/27. | `IMPLEMENTATION_LOG.md` (2026-11-11) |
| IDs públicos en el módulo de red | Las rutas `/api/network/hosts/{hostId}` y similares siguen usando IDs numéricos. La resolución está protegida por el filtro de cuenta y por la verificación de proyecto, pero el ID numérico es adivinable. | `reports/fase-04.md` |

## 3. Límites de diseño

- **Estado en memoria del proceso.** Los contadores de intentos de login, los cubos de *rate limit* (Bucket4j y Caffeine) y el registro de sesiones (`SessionRegistry`) viven en la memoria de una sola instancia. Reiniciar la aplicación los borra. No coordinan varias réplicas. Existe una excepción parcial: `cuenta.sesiones_revocadas_en` persiste la revocación de sesiones de una cuenta.
- **Una sola instancia.** El WebSocket y los bloqueos de escaneo asumen un único proceso. Los bloqueos de escaneo usan `GET_LOCK` de MySQL, que sí sirve entre instancias, pero el resto no se probó con más de una.
- **Un equipo local concentra todo.** La base de datos, el `.env` con las claves, el certificado local, los respaldos y la aplicación viven en el mismo equipo. Quien controle ese equipo controla los datos de todas las cuentas. No hay cifrado de la base en reposo más allá del secreto TOTP.
- **Módulo de red dependiente del sistema.** El descubrimiento lee la tabla de vecinos con PowerShell en Windows (`Get-NetNeighbor`) y tiene una ruta de análisis para Linux; usa `nmap` si está instalado. No se probó en otros entornos. Las pruebas usan dobles y **no** generan tráfico real.
- **El escaneo depende de la declaración del auditor.** `ScopeGuard` comprueba que el rango esté declarado y que la interfaz activa caiga dentro de él. No puede comprobar que la persona *tenga* autorización legal sobre esa red; la referencia a la carta o contrato es texto libre que ella escribe.
- **`poll-paths` compara solo la ruta, no el método.** Las consultas periódicas de la pantalla de red no cuentan como actividad, pero `DELETE /api/network/history` (borrar historial) comparte ruta con `GET /api/network/history` y tampoco la cuenta. Además, las rutas de `poll-paths` usan el cubo de límite `poll` (30 por minuto) en lugar de `api-general` (120). Ninguno de los dos efectos abre un acceso: solo afecta cuándo caduca la sesión y cuántas peticiones se admiten.
- **TOTP con SHA-1.** Es lo que soportan las aplicaciones autenticadoras corrientes (RFC 6238). No hay WebAuthn ni claves de seguridad.
- **Cuota de NVD sin confirmar con la fuente.** Los límites con y sin clave (`tinyhat.nvd.requests-with-key` y `requests-without-key`) son valores configurables que no se pudieron confirmar con la documentación oficial: el sitio de NVD entregó una página sin contenido documental al intentar verificarlos (Fase 6). La caché local es la defensa principal.
- **Cuentas de prueba en la base compartida.** Las validaciones de las fases 11 y 12 dejaron auditores, proyectos y borradores ficticios en `hexora_db` (marcados como `auditor.validacion.fase11.*@tinyhat.local` y similares). No se limpiaron. Además, la migración V29 activa a mano dos usuarios de prueba (`auditor@auditor.com` y `lokefev730@jcaton.com`) porque `dev` no tiene SMTP; en cualquier otro entorno donde esos correos se registren sin verificar, la migración los activaría. Conviene retirar esa migración antes de cualquier despliegue real (hay que hacerlo con una migración nueva, no editando V29).

## 4. Pendientes de verificación por la persona

| Tema | Qué falta |
|---|---|
| F13-CA2 | Abrir un PR de prueba con un secreto falso y confirmar que el job de secretos falla en GitHub. |
| Dependency-Check completo | Solo se comprobó que arranca. Falta una ejecución completa con `NVD_API_KEY` como secreto del repositorio. |
| CodeQL | Sigue con `continue-on-error`. Hay que quitarlo cuando el primer PR confirme que extrae Java 25. |
| Protección de `main` | Configuración manual en GitHub (`docs/security/branch-protection.md`). El agente no toca la configuración del repositorio. |
| Verificación visual de la CSP | La CSP está en modo de aplicación y 29 comprobaciones automáticas pasan, pero falta que alguien recorra la interfaz en un navegador y confirme que no hay violaciones. |
| `hexora/target` versionado | **Resuelto en el índice** (2026-11-15): 588 archivos retirados con `git rm -r --cached`; quedan en disco y `.gitignore` los excluye. Falta que la persona haga el commit. Siguen en el historial anterior. |
| Rotación de secretos | El agente no rota secretos. La persona decide si rota las claves de su `.env` antes de publicar el repositorio. |

## 5. Qué hacer si esto cambia

- Si se añade un segundo servidor, mover contadores, cubos y sesiones a un almacén compartido (por ejemplo Redis o Spring Session JDBC) antes de confiar en el límite de intentos.
- Si se añade un rol ADMIN, repetir la matriz del Apéndice B y las pruebas de aislamiento: el plan decidió que no existe y todo el modelo de permisos lo asume.
- Si se implementa OAuth/OIDC, usar la siguiente migración libre (la numeración V26 ya está ocupada por tokens) y repetir F9-CA1 a F9-CA8.
