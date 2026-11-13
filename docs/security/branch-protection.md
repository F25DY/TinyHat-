# Protección de la rama principal (pasos manuales)

Estos pasos los hace una persona con permisos de administración en GitHub. El agente de programación no modifica la configuración del repositorio, la protección de ramas, los secretos ni las Actions (regla R11).

Repositorio: `https://github.com/F25DY/TinyHat-` (antes `bertelsarasofia-code/Hexora-`; GitHub redirige el nombre antiguo). Si tu copia local sigue apuntando al nombre viejo, actualízala:

```powershell
git remote set-url origin https://github.com/F25DY/TinyHat-.git
```

## 1. Reglas para `main`

`Settings` > `Rules` > `Rulesets` > `New branch ruleset` (o `Settings` > `Branches` > `Add branch protection rule`), con destino la rama `main`:

- [ ] **Require a pull request before merging**, con al menos 1 aprobación.
- [ ] **Dismiss stale pull request approvals when new commits are pushed.**
- [ ] **Require conversation resolution before merging.**
- [ ] **Require status checks to pass** y **Require branches to be up to date**. Marca como obligatorios estos cuatro jobs del workflow `security` (el nombre exacto aparece en la lista de GitHub después de la primera ejecución):
  - `Compilación y pruebas`
  - `Secretos (gitleaks)`
  - `Dependencias (OWASP Dependency-Check)`
  - `SAST (SpotBugs y FindSecBugs)`
- [ ] **Block force pushes.**
- [ ] **Restrict deletions.**
- [ ] **Do not allow bypassing the above settings** (incluye a administradores).

`SAST (CodeQL)` no se marca como obligatorio todavía. Ver `ci-cd.md`.

## 2. Permisos de Actions

`Settings` > `Actions` > `General`:

- **Workflow permissions:** `Read repository contents and packages permissions`.
- Desmarcar **Allow GitHub Actions to create and approve pull requests**.
- **Fork pull request workflows from outside collaborators:** `Require approval for all outside collaborators`.

## 3. Secretos y análisis automáticos de GitHub

- `Settings` > `Secrets and variables` > `Actions` > `New repository secret`: crea `NVD_API_KEY` con la clave gratuita de https://nvd.nist.gov/developers/request-an-api-key. Es la misma variable que usa la aplicación en `.env`; no hace falta que sea la misma clave.
- `Settings` > `Code security`: activar **Dependabot alerts**, **Secret scanning** y **Push protection** (gratuitos en repositorios públicos). Dependabot solo abre pull requests; no hay fusión automática.

## 4. Verificación manual con un pull request de prueba (F13-CA2)

1. Crea una rama desde la que vayas a abrir el PR (no trabajes sobre `main`):

   ```powershell
   git switch -c prueba/ci-secreto
   ```

2. Genera un secreto **falso** en el momento y guárdalo en un archivo nuevo. No lo copies de ningún documento: un valor escrito en el repositorio sería un hallazgo real.

   ```powershell
   "token = ghp_$(-join ((1..18) | ForEach-Object { '{0:x2}' -f (Get-Random -Maximum 256) }))" | Set-Content hexora/src/main/prueba-secreto.txt
   git add hexora/src/main/prueba-secreto.txt
   git commit -m "Prueba de CI: secreto falso"
   git push -u origin prueba/ci-secreto
   ```

3. Abre un pull request hacia `main`. Resultado esperado:
   - `Secretos (gitleaks)` **falla** y señala la regla `github-pat` en ese archivo (el valor aparece oculto).
   - `Compilación y pruebas` y `SAST (SpotBugs y FindSecBugs)` pasan.
   - `Dependencias` pasa, o muestra la advertencia "omitido" si el secreto `NVD_API_KEY` no está configurado.
   - No se puede fusionar mientras falle un check obligatorio.

4. Cierra el PR **sin fusionar** y borra la rama. El secreto era falso, pero no debe llegar a `main`:

   ```powershell
   git switch seguridad/fase-13
   git push origin --delete prueba/ci-secreto
   git branch -D prueba/ci-secreto
   ```

5. Revisa la pestaña `Actions` del PR: los artefactos `informe-spotbugs` y `informe-dependency-check` deben estar adjuntos, y ningún job debe mostrar permisos de escritura en "Set up job".
