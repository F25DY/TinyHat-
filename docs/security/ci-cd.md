# Pipeline de seguridad (CI/CD) — Fase 13

Workflow: `.github/workflows/security.yml`. Se ejecuta en cada pull request hacia `main` y, como verificación posterior a la fusión, en cada push a `main`. No despliega, no publica, no escribe en el repositorio ni fusiona nada.

## Qué comprueba cada job

| Job | Herramienta | Falla cuando | Se corre en local con |
|---|---|---|---|
| Compilación y pruebas | `mvnw clean verify` (Java 25) | Falla cualquier prueba, incluidas las de seguridad y las de aislamiento entre cuentas | `cd hexora; .\mvnw.cmd -B verify` |
| Secretos (gitleaks) | gitleaks 8.30.1, imagen fijada por huella | Hay un secreto en el árbol de trabajo o en cualquier commit de cualquier rama | Comandos de abajo |
| Dependencias | OWASP Dependency-Check 13.0.0 | Una dependencia tiene una vulnerabilidad con CVSS 7.0 o más | `.\mvnw.cmd -Psecurity-scan -DskipTests dependency-check:check` con `NVD_API_KEY` definida |
| SAST (SpotBugs y FindSecBugs) | SpotBugs 4.10.5 y FindSecBugs 1.14.0 | Hay un hallazgo de rango 14 o menor con confianza media o alta | `.\mvnw.cmd -Psecurity-scan -DskipTests compile spotbugs:check` |
| SAST (CodeQL) | CodeQL, consultas `security-extended` | Hay un hallazgo con severidad de seguridad 7.0 o más. **No bloquea todavía.** | Solo en GitHub |

Los análisis estáticos y de dependencias viven en el perfil Maven `security-scan` del `pom.xml`, para que `mvnw verify` normal siga rápido.

## Secretos en local

Con Docker encendido, desde la raíz del repositorio (PowerShell). Los valores se ocultan siempre (`--redact`).

```powershell
# Árbol de trabajo. Ojo: si existen hexora/.env o hexora/local-dev.p12 (locales, ignorados por git) los marcará;
# es lo esperado y nunca llegan a GitHub.
docker run --rm -v "${PWD}:/repo:ro" ghcr.io/gitleaks/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f dir /repo --config /repo/.gitleaks.toml --redact --no-banner

# Todo el historial de todas las ramas
docker run --rm -v "${PWD}:/repo:ro" -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0=/repo ghcr.io/gitleaks/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f git /repo --config /repo/.gitleaks.toml --log-opts="--all" --redact --no-banner
```

La configuración `.gitleaks.toml` usa todas las reglas por defecto y solo permite un falso positivo conocido: la regla `generic-api-key` dentro de `hexora/src/test/java`, donde viven valores ficticios de prueba (regla R9). Cualquier otra regla sigue activa también ahí.

## Qué hacer cuando un job falla

| Falla | Qué significa | Qué hacer |
|---|---|---|
| gitleaks | Hay un secreto real o con forma de secreto | Si es real: **rotarlo primero** y quitarlo del código. Si es ficticio de pruebas: usar un valor claramente inventado. Nunca añadir excepciones para silenciarlo sin revisión |
| Dependency-Check | Una librería tiene una CVE grave | Actualizar la dependencia. Si no hay arreglo o no aplica, registrar una supresión en `hexora/dependency-check-suppressions.xml` con el motivo, la CVE y una fecha de revisión; una persona la aprueba en el PR |
| SpotBugs | Posible vulnerabilidad en el código | Corregirla. Si es un falso positivo demostrado, añadir una exclusión en `hexora/spotbugs-exclude.xml`, limitada a una clase y con justificación escrita |
| Compilación y pruebas | Una prueba falla | Corregir el código, no la prueba (regla R4) |

## Mantenimiento

- **Acciones fijadas por SHA.** Todas las acciones de terceros usan el SHA de commit completo, con la versión en un comentario. Dependabot propone las actualizaciones cada lunes, y una persona las revisa. Para fijar una versión nueva a mano: `git ls-remote https://github.com/<dueño>/<acción> "refs/tags/<versión>" "refs/tags/<versión>^{}"`; si aparece la línea con `^{}`, ese es el SHA del commit.
- **Imágenes por huella.** gitleaks se fija por `sha256`. Para actualizarla: `docker pull ghcr.io/gitleaks/gitleaks:latest` y leer `docker image inspect ghcr.io/gitleaks/gitleaks:latest --format "{{index .RepoDigests 0}}"`.
- **Línea base de historial.** Si algún día el historial contiene un hallazgo ya rotado que no se puede reescribir, se registra en `.gitleaks-baseline.json` (revisado por una persona) y el job lo aplica automáticamente.
- **CodeQL.** Corre sin bloquear hasta que el primer pull request confirme que extrae bien el código Java 25. Entonces: quitar `continue-on-error: true` del job `sast-codeql` y añadir `SAST (CodeQL)` a los checks obligatorios. No sube resultados a la pestaña *Code scanning* porque eso exige el permiso `security-events: write`, que la regla de la fase prohíbe; guarda un SARIF como artefacto y falla por severidad.

## Límites conocidos

- **Pull requests desde forks.** GitHub no entrega secretos a esos PRs, así que `Dependencias` se omite con una advertencia visible. Repetir el análisis desde una rama del propio repositorio.
- **`hexora/target` está versionado** (599 archivos). El pipeline usa `clean` para que no contamine el build, pero conviene retirarlo del control de versiones (ya figura en `hexora/correcciones.md`).
- **DAST (opcional en el plan) no se incluyó.** Un escaneo base de ZAP sin sesión solo recorre el login y el registro; no aporta mientras no exista una sesión autenticada de prueba. Queda como mejora para cuando se defina el flujo de informes.
- **Dependency-Check solo se probó hasta su arranque en local** (necesita `NVD_API_KEY` y mucho tiempo la primera vez). Su ejecución completa se verifica en el PR de prueba.
