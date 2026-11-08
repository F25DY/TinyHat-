# Security implementation deviations

## 2026-11-07 — V18 foreign-key name and partial database state

- The shared MySQL database recorded V18 as failed after the account table and organization rename/columns had been created. The existing auditor-to-organization foreign key uses a server-generated name rather than the name assumed by the migration.
- V18 now discovers and removes the actual foreign-key name from `information_schema` before renaming `empresa_id` to `organizacion_cliente_id`. This was validated from a clean V1–V17 schema in disposable MySQL.
- The shared database was not repaired, restored, or migrated. The user-provided `Dump20261108.sql` was inspected read-only: it records V18 with `success=0`, keeps `auditoria.empresa_id` and its old FK, and includes the partial `cuenta`/`organizacion_cliente` changes. It is not a pre-V18 recovery copy. Do not import it or run Flyway against the shared database.

## 2026-11-07 — Normalized monitored-port migration

- V24 is currently used to copy the legacy `network_host.monitored_ports` values into `monitored_port`, then drop the redundant text column.
- The original roadmap reserves V24 for MFA. Before implementing V25–V27, revise and approve the migration map; the expected shift is MFA V25, security tokens V26, external identity V27, and authorized scan ranges V28.
- Canonical network-asset unification and account-scoped composite relationships across the network tables remain incomplete. Do not consider the network schema redesign complete or apply it to the shared database.

## 2026-11-07 — Integration-test datasource isolation

- The context-load and network web integration tests now use a shared disposable MySQL Testcontainer instead of inheriting the developer's `.env` datasource.
- Network web tests now provide an authenticated auditor and CSRF tokens for state-changing requests while retaining the security filters.
