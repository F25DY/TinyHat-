# Privilegios de base de datos (Fase 11, tarea 7)

> **Actualización Fase 12.** Los permisos de ejecución ya no se conceden a nivel de base, sino tabla por tabla: `audit_event` solo `SELECT` e `INSERT`, `flyway_schema_history` sin acceso, el resto con DML. El script `GRANT ... ON hexora_db.*` de más abajo queda como referencia del contrato inicial de la Fase 11.
>
> **Actualización (mantenimiento posterior a la Fase 14): ahora es automático.** Tras cada migración, la aplicación concede esos permisos por sí misma (`RuntimePrivilegeMigrationStrategy`), así que ya no hay que acordarse de ejecutar `hexora/database/grant-runtime-privileges.sql`. Ese script se conserva como alternativa manual. Ver la sección "Concesión automática".

## Concesión automática

Al arrancar, Flyway migra con la cuenta de migración y, a continuación, esa misma conexión concede al usuario de ejecución (`spring.datasource.username`):

| Objeto | Permisos |
|---|---|
| Cada tabla de negocio, incluidas las que cree una migración futura | `SELECT`, `INSERT`, `UPDATE`, `DELETE` |
| `audit_event` (y cualquier tabla de `tinyhat.db.runtime-grants.append-only-tables`) | `SELECT`, `INSERT` |
| `flyway_schema_history` | ninguno |
| La base completa, DDL, `GRANT OPTION` | ninguno (se retira el permiso a nivel de base que crea el script de inicio) |

Es idempotente: se puede ejecutar en cada arranque y el resultado no depende de permisos anteriores.

**Requisito: la cuenta de migración necesita `GRANT OPTION` sobre la base de datos de la aplicación** (solo sobre esa base, nunca global). Cómo se obtiene:

- **Volumen de Docker nuevo:** `hexora/database/init/01-runtime-user.sh` ya se lo concede a `MYSQL_USER`, siempre que no sea la misma cuenta que la de ejecución.
- **Base existente** (volumen ya creado, servidor propio): una sola vez, como administrador:

  ```sql
  GRANT ALL PRIVILEGES ON `hexora\_db`.* TO '<cuenta-de-migración>'@'%' WITH GRANT OPTION;
  ```

  > **Escribe el `_` del nombre de la base escapado (`hexora\_db`).** En MySQL, `_` sin escapar es un comodín y crea una fila de privilegios distinta de la que ya tiene la cuenta (la imagen de Docker la crea con el nombre escapado y sin `GRANT OPTION`). MySQL da prioridad a la fila escapada y la otra queda ignorada: el `GRANT OPTION` parece concedido en `SHOW GRANTS` pero no surte efecto, y la concesión automática falla con el error 1142. Se descubrió al probar con la imagen real; `RuntimePrivilegeGrantIntegrationTest` lo cubre. Para comprobarlo: en `SHOW GRANTS FOR '<cuenta>'@'%'` debe aparecer ``GRANT ALL PRIVILEGES ON `hexora\_db`.* ... WITH GRANT OPTION`` (con la barra).

**Cuándo se aplica y qué pasa si falla**

| Situación | Comportamiento |
|---|---|
| Usuario de ejecución y de migración son la misma cuenta (típico en local de un solo usuario) | No hace nada y lo registra en el log |
| `tinyhat.db.runtime-grants.enabled=false` | No hace nada; hay que usar el script manual |
| La cuenta de migración no puede conceder (falta `GRANT OPTION`), perfil `prod` | **El arranque se detiene** con el comando que lo corrige. Es preferible no arrancar a arrancar con permisos sin aplicar |
| Lo mismo, otros perfiles | Registra un `ERROR` con el comando y la aplicación continúa |

**Un fallo no quita permisos.** La operación primero solo añade (`GRANT` por tabla) y retira lo que sobra después. Si la cuenta de migración no puede conceder, el primer `GRANT` falla y el usuario de ejecución conserva exactamente lo que ya tenía. (Una primera versión revocaba antes de conceder y, al fallar, dejaba al usuario de ejecución sin ningún permiso; se corrigió y `aFailedAttemptLeavesTheExistingPrivilegesUntouched` lo vigila.)

Propiedades (en `application.properties`): `tinyhat.db.runtime-grants.enabled` (por defecto `true`), `tinyhat.db.runtime-grants.host` (`%`) y `tinyhat.db.runtime-grants.append-only-tables` (`audit_event`).

**Cómo se evita la inyección de SQL.** `GRANT`/`REVOKE` no admiten parámetros para nombres de objeto, y concatenar cadenas en Java lo marca SpotBugs/FindSecBugs (`SQL_INJECTION_JDBC`). Por eso todo el SQL que ejecuta JDBC es constante: los nombres (base, tabla, usuario, host) entran como parámetros `?` de una sentencia fija que construye el `GRANT`/`REVOKE` en el servidor, doblando allí los acentos graves y las comillas, y se ejecuta con `PREPARE`/`EXECUTE`. Además, usuario, host y base se validan con una lista cerrada de caracteres. Los nombres de tabla salen de `information_schema` y pueden contener casi cualquier carácter: una prueba usa una tabla llamada ``ev`il' TO 'intruso'@'%' --x`` para demostrar que no se puede escapar del identificador.

**Comprobación final desde la cuenta de ejecución.** Tras conceder, la aplicación ejecuta `SHOW GRANTS` con su propia cuenta y, si encuentra algún permiso **a nivel de base**, lo trata como fallo (detiene el arranque en `prod`, `ERROR` en el resto). Un permiso de base taparía la restricción de solo agregar de `audit_event`. Es necesaria porque la cuenta de migración no puede ver esos permisos ni retirar uno concedido con el nombre de base sin escapar (error 1044). Si aparece, como administrador: ``REVOKE ALL PRIVILEGES ON `<base>`.* FROM '<usuario-de-ejecución>'@'%';``

**Evidencia:**
- `RuntimePrivilegeGrantIntegrationTest` (10 pruebas, MySQL 8.0.46 real y desechable que ejecuta el script de inicio auténtico) comprueba los permisos exactos por tabla; que el DDL, `UPDATE` y `DELETE` sobre `audit_event`, y la lectura del historial de Flyway quedan denegados; que una tabla creada por una migración posterior queda cubierta; que se retiran los permisos sobrantes de estados anteriores; que un fallo no modifica nada; la detección de un permiso de base irrevocable; la política de fallo en `prod` y el rechazo de nombres con inyección. Verificado por mutación (con `audit_event` con DML completo y sin revocación de sobrantes fallan 4 pruebas; con la comprobación final desactivada falla la de permisos de base).
- Con la aplicación real (JAR de esta rama, perfil por defecto) y una base creada con `01-runtime-user.sh`: 25 de 25 tablas con permisos, `audit_event` = `INSERT,SELECT`, 0 permisos de base, sin ejecutar ningún script manual. Con una base antigua cuya cuenta de migración no tiene `GRANT OPTION`: `ERROR` con el comando, la aplicación arranca y los permisos previos quedan intactos.

## Contrato

| Cuenta | Uso | Privilegios sobre `hexora_db` | Prohibido |
|---|---|---|---|
| Usuario de ejecución (`DB_USER`, p. ej. `hexora_app`) | Conexión de la aplicación en tiempo de ejecución (Hibernate con `ddl-auto=validate`) | `SELECT`, `INSERT`, `UPDATE`, `DELETE` | `CREATE`, `ALTER`, `DROP`, `INDEX`, `REFERENCES`, `GRANT`, privilegios globales |
| Usuario de migración (`FLYWAY_DB_USER`) | Solo Flyway al arrancar (`spring.flyway.*`) | DML más `CREATE`, `ALTER`, `DROP`, `INDEX`, `REFERENCES`, `CREATE VIEW`, `TRIGGER`, `LOCK TABLES` | Privilegios globales; no debe usarse como datasource de la aplicación |

Reglas:

- `spring.datasource.url` **no** lleva `createDatabaseIfNotExist=true`; solo la URL de Flyway lo lleva, porque crear el esquema es una tarea de migración.
- En `prod` el perfil exige `FLYWAY_DB_USER`/`FLYWAY_DB_PASSWORD` y `ProfileSecurityGuard` rechaza el arranque si el usuario de Flyway es el mismo que el de ejecución.
- `audit_event` queda pendiente para la Fase 12: allí se restringirá el usuario de ejecución a `SELECT`/`INSERT` sobre esa tabla (permisos por tabla en MySQL).
- `GET_LOCK`/`RELEASE_LOCK` (bloqueos de escaneo) no requieren privilegios adicionales.

## Script de creación (MySQL 8)

Sustituye las contraseñas por valores generados (por ejemplo `openssl rand -base64 24`) y no los versiones.

```sql
-- Usuario de ejecución: solo DML.
CREATE USER IF NOT EXISTS 'hexora_app'@'%' IDENTIFIED BY '<contraseña_app>';
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'hexora_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON `hexora_db`.* TO 'hexora_app'@'%';

-- Usuario de migración: DDL acotado al esquema.
CREATE USER IF NOT EXISTS 'hexora_flyway'@'%' IDENTIFIED BY '<contraseña_flyway>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES,
      CREATE VIEW, TRIGGER, LOCK TABLES ON `hexora_db`.* TO 'hexora_flyway'@'%';
FLUSH PRIVILEGES;
```

## Verificación

```sql
SHOW GRANTS FOR 'hexora_app'@'%';
-- Como hexora_app, cada sentencia debe fallar con ERROR 1142 (command denied):
CREATE TABLE hexora_db.ddl_check (id INT);
ALTER TABLE hexora_db.cuenta ADD COLUMN ddl_check INT;
DROP TABLE hexora_db.informe;
```

## Estado en el entorno local (Docker `hexora-mysql`, 2026-11-11)

- `docker-compose.yml` crea al iniciar el volumen el usuario `MYSQL_USER` con `ALL PRIVILEGES` sobre la base. Ese usuario (`admin1`) se conserva como **usuario de migración** (`FLYWAY_DB_USER`).
- Se creó `hexora_app` con `SELECT, INSERT, UPDATE, DELETE` sobre `hexora_db.*` y pasó a ser `DB_USER` en `.env`.
- Se comprobó que `CREATE TABLE`, `ALTER TABLE` y `DROP TABLE` fallan con `hexora_app` y que la aplicación arranca con `ddl-auto=validate` usando ese usuario; la evidencia está en `reports/fase-11.md`.
- Si se recrea el volumen de Docker con `.env` actual, `MYSQL_USER` será `hexora_app` con todos los privilegios: ejecuta el script anterior después para volver a separar cuentas.
