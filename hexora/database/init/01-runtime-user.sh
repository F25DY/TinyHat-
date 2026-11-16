#!/bin/bash
# Runs once, when the MySQL data volume is created (mounted into /docker-entrypoint-initdb.d).
# MYSQL_USER (the migration account) already owns the schema through the image's own setup. This script
# creates the separate runtime account, with DML only; per-table privileges are then tightened by
# hexora/database/grant-runtime-privileges.sql after the first Flyway migration.
set -euo pipefail

if [ -z "${TINYHAT_RUNTIME_USER:-}" ] || [ -z "${TINYHAT_RUNTIME_PASSWORD:-}" ]; then
  echo "TINYHAT_RUNTIME_USER / TINYHAT_RUNTIME_PASSWORD not set: skipping runtime account creation"
  exit 0
fi

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${TINYHAT_RUNTIME_USER}'@'%' IDENTIFIED BY '${TINYHAT_RUNTIME_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO '${TINYHAT_RUNTIME_USER}'@'%';
FLUSH PRIVILEGES;
SQL
