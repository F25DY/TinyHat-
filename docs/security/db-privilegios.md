# Privilegios de base de datos (Fase 11, tarea 7)

> **Actualización Fase 12.** Los permisos de ejecución ya no se conceden a nivel de base, sino tabla por tabla con `hexora/database/grant-runtime-privileges.sql`: `audit_event` solo `SELECT` e `INSERT`, `flyway_schema_history` sin acceso, el resto con DML. Hay que volver a ejecutarlo después de cualquier migración que cree tablas. El script `GRANT ... ON hexora_db.*` de más abajo queda como referencia del contrato inicial de la Fase 11.

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
