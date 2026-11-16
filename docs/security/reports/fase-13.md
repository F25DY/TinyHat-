# Fase 13 — Pipeline CI/CD (GitHub Actions)

## 1. Estado

**Implementación completa; pendiente de la verificación manual F13-CA2 y de aprobación.** El workflow no puede ejecutarse en GitHub hasta que la rama se suba y se abra un pull request, así que todo lo que sí se podía comprobar en local se comprobó, y lo demás queda como pasos para la persona (§8).

Rama: `seguridad/fase-13`, creada desde `seguridad/fase-11` después de tu commit "Desarrollo hasta fase 12". Los archivos de esta fase siguen sin commit.

Respaldo previo a los cambios: `C:\Users\Fredy\OneDrive\Documentos\dumps\fase-13\20261113_114322\` (árbol de trabajo sin `target`, `.env` y sumas SHA-256). La fase no toca la base de datos.

## 2. Qué se implementó

| Archivo | Propósito |
|---|---|
| `.github/workflows/security.yml` | Pipeline con cinco jobs: pruebas, secretos, dependencias, SAST SpotBugs y SAST CodeQL |
| `.github/dependabot.yml` | Maven y GitHub Actions, semanal, solo abre pull requests |
| `.gitleaks.toml` | Reglas por defecto más una única excepción acotada |
| `hexora/pom.xml` | Perfil `security-scan` con SpotBugs, FindSecBugs y OWASP Dependency-Check |
| `hexora/spotbugs-exclude.xml` | Exclusiones, cada una limitada a una clase y con su justificación |
| `hexora/dependency-check-suppressions.xml` | Vacío y documentado |
| `docs/security/branch-protection.md` | Pasos manuales de protección de rama, Actions y secretos |
| `docs/security/ci-cd.md` | Cómo funciona el pipeline, cómo correrlo en local y cómo mantenerlo |

Reglas duras del plan, cumplidas: permisos de solo lectura en workflow y jobs, sin `pull_request_target`, sin `workflow_dispatch`, sin despliegue ni publicación, sin `git push`, commits, etiquetas ni fusión automática, y todas las acciones fijadas por SHA de commit completo. Las imágenes de contenedor se fijan por huella (`sha256`).

## 3. Migraciones

Ninguna. La base `hexora_db` sigue en V28.

## 4. Criterios de aceptación

| ID | Cómo se verifica | Comando o prueba | Resultado |
|---|---|---|---|
| F13-CA1 | El YAML valida y los comandos corren en local | `actionlint 1.7.12`: sin problemas. `mvnw -B verify`: 248 pruebas verdes. `spotbugs:check`: 0 hallazgos. gitleaks: sin hallazgos. Dependency-Check: resuelve y arranca; no se pudo ejecutar completo sin `NVD_API_KEY` | **PASA** con una salvedad (Dependency-Check completo) |
| F13-CA2 | La persona abre un PR de prueba con un secreto falso y el job de secretos falla | Pasos en `branch-protection.md` §4. En local se comprobó que gitleaks detecta un token falso generado en el momento, también dentro de `src/test` | **PENDIENTE de la persona** |
| F13-CA3 | Ninguna acción usa etiquetas móviles | Búsqueda de `uses:` sin SHA de 40 caracteres: ninguna. Búsqueda de `@main`, `@latest`, `@vN`: ninguna | **PASA** |
| F13-CA4 | Sin `write` en `permissions`, ni `pull_request_target`, ni `git push`, `git commit`, `gh pr merge`, `--force`, ni despliegue o publicación | Búsqueda en `.github/workflows` y `dependabot.yml`: las únicas coincidencias son tres comentarios que explican las reglas. Único permiso declarado: `contents: read` | **PASA** |

Búsqueda ejecutada para F13-CA4:

```
grep -rniE "pull_request_target|workflow_dispatch|git push|git commit|gh pr merge|--force|deploy|publish|auto-?merge|git tag|gh release|: *write" .github/workflows .github/dependabot.yml
```

Resultado: solo las líneas 5, 6 y 194 de `security.yml`, que son comentarios.

## 5. Validación ejecutada

```powershell
cd hexora
.\mvnw.cmd -B verify
```

Resultado: **248 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS.**

Escaneos de secretos (gitleaks 8.30.1, valores ocultos):

| Alcance | Sin `.gitleaks.toml` | Con `.gitleaks.toml` |
|---|---|---|
| Historial completo, todas las ramas (85 commits) | 11 hallazgos | **0** |
| Árbol versionable (copia tipo CI) | 7 | **0** |
| Árbol real con `.env` y `local-dev.p12` locales | 11 | 4 (solo esos archivos locales) |

Los 11 hallazgos eran valores ficticios en archivos de prueba (clave base64 de "abcdef...", secreto de ejemplo del RFC 6238, clave de ejemplo del RFC 6455, tokens inventados). Los 4 del árbol real son `hexora/.env` y `hexora/local-dev.p12`: archivos locales ignorados por git que **nunca estuvieron en el historial**. No hay secretos reales versionados.

Prueba de mutación del SAST: se añadió temporalmente un endpoint con inyección SQL por concatenación y un hash MD5. Con el filtro final, SpotBugs falló con 2 hallazgos (`SQL_INJECTION_SPRING_JDBC` y `WEAK_MESSAGE_DIGEST_MD5`); al retirar la clase pasó con 0. La clase de prueba se eliminó y no queda rastro en git.

## 6. Hallazgos durante la fase

- **El primer filtro de SpotBugs no protegía.** Con `maxRank=9` la mutación pasó sin ser detectada, porque FindSecBugs clasifica la inyección SQL en rango 12 y MD5 en rango 10. Se subió el límite a 14 (rangos "scariest", "scary" y "troubling") con confianza media o alta, y se verificó con la mutación.
- **23 hallazgos reales de SpotBugs en el código del proyecto**, clasificados uno por uno:
  - **5 avisos de posible nulo, corregidos en 3 lugares:** `ApiExceptionHandler.methodNotAllowed`, `MfaGateFilter` y `UserRateLimitFilter`. Ahora comprueban el tipo del principal en vez de asumirlo.
  - **6 `COMMAND_INJECTION` justificados** en el módulo de red: todos usan `ProcessBuilder` con lista de argumentos, sin shell, con valores del propio sistema (un `InetAddress` ya validado, un número de interfaz del JVM, una red calculada). Ninguno recibe datos de una petición HTTP.
  - **9 `UNVALIDATED_REDIRECT` justificados**: todos redirigen a `contextPath` más una ruta constante.
  - **1 `UNENCRYPTED_SOCKET` justificado**: la sonda TCP de puertos no envía datos.
  - **2 `ENTITY_LEAK` justificados** en `CveController`: devuelve la caché pública de NVD, sin datos de cuentas.
- **`mvnw clean` falla en tu máquina** porque algún proceso (IDE u OneDrive) mantiene archivos de `target` abiertos. En el runner de GitHub no ocurre; el pipeline usa `clean` a propósito porque `hexora/target` está versionado.
- **El repositorio cambió de nombre:** ahora es `github.com/F25DY/TinyHat-` y es público. Tu remoto local todavía apunta al nombre antiguo, que GitHub redirige.

## 7. Desviaciones

Registradas en `IMPLEMENTATION_LOG.md` (entrada del 2026-11-13). Las principales:

1. **CodeQL no sube resultados** a la pestaña "Code scanning", porque eso exige `security-events: write` y la regla de la fase prohíbe permisos de escritura. Guarda un SARIF como artefacto y falla por severidad de seguridad 7.0 o más. Queda **sin bloquear** hasta confirmar en el primer PR que extrae bien Java 25.
2. **DAST (opcional) no se incluyó:** un escaneo base de ZAP sin sesión solo recorre el login y el registro.
3. **gitleaks corre como imagen Docker fijada por huella**, no como acción de marketplace, para evitar licencias y una dependencia de terceros más.
4. **El historial se escanea en cada ejecución**, no una sola vez: hoy está limpio, así que bloquea sin costo. Una línea base permite aceptar hallazgos antiguos ya rotados.
5. **Excepción de gitleaks** solo para `generic-api-key` en `hexora/src/test/java`.

## 8. Riesgos abiertos y pasos para la persona

1. **Verificar el pipeline en GitHub (F13-CA2).** Sigue `docs/security/branch-protection.md` §4: sube la rama, abre un PR, comprueba que gitleaks falla con un secreto falso y que los demás jobs pasan.
2. **Crear el secreto `NVD_API_KEY`** en `Settings > Secrets and variables > Actions`. Sin él, el job de dependencias se omite con una advertencia.
3. **Configurar la protección de `main`** (`branch-protection.md` §1 a §3). Yo no modifico la configuración del repositorio.
4. **Dependency-Check completo** solo se probó hasta su arranque; su primera ejecución en GitHub tarda y descarga la base de vulnerabilidades.
5. **CodeQL sin bloquear** hasta el primer PR verde; después hay que quitar `continue-on-error: true` (ver `ci-cd.md`).
6. **`hexora/target` sigue versionado** (599 archivos). Conviene retirarlo del índice de git; ya figuraba en `hexora/correcciones.md`.
7. **Candidato de mejora:** `CveController` podría devolver un DTO en lugar de la entidad.
8. **Actualizar el remoto local** al nuevo nombre del repositorio.
9. **Commit.** Los cambios de esta fase siguen sin commit en `seguridad/fase-13`.

**No iniciar la Fase 14 hasta recibir `APROBADA FASE 13`.**
