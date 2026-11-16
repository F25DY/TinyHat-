#!/bin/bash
# Runs once, when the MySQL data volume is created (mounted into /docker-entrypoint-initdb.d).
# MYSQL_USER (the migration account) already owns the schema through the image's own setup. This script
# creates the separate runtime account, with DML only, and gives the migration account GRANT OPTION on the
# application database so that the application can tighten the runtime account's privileges table by table
# after every Flyway migration (RuntimePrivilegeMigrationStrategy; manual alternative:
# hexora/database/grant-runtime-privileges.sql).
set -euo pipefail

if [ -z "${TINYHAT_RUNTIME_USER:-}" ] || [ -z "${TINYHAT_RUNTIME_PASSWORD:-}" ]; then
  echo "TINYHAT_RUNTIME_USER / TINYHAT_RUNTIME_PASSWORD not set: skipping runtime account creation"
  exit 0
fi

# The database name MUST be written with "_" escaped ("hexora\_db") in every GRANT below. In MySQL an unescaped "_" is
# a wildcard and creates a DIFFERENT privilege entry from the escaped one that the image creates for MYSQL_USER:
#   * GRANT OPTION on the unescaped name is silently ignored in favour of the escaped entry (GRANT then fails, 1142);
#   * the migration account cannot revoke an entry written with the unescaped name (error 1044), so a runtime
#     privilege granted that way could not be removed by the application afterwards.
ESCAPED_DATABASE="${MYSQL_DATABASE//_/\\_}"

# GRANT OPTION only for a migration account that is NOT the runtime account (never give it to the runtime one).
MIGRATION_GRANT=""
if [ -n "${MYSQL_USER:-}" ] && [ "${MYSQL_USER}" != "${TINYHAT_RUNTIME_USER}" ]; then
  MIGRATION_GRANT="GRANT ALL PRIVILEGES ON \`${ESCAPED_DATABASE}\`.* TO '${MYSQL_USER}'@'%' WITH GRANT OPTION;"
fi

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS '${TINYHAT_RUNTIME_USER}'@'%' IDENTIFIED BY '${TINYHAT_RUNTIME_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${ESCAPED_DATABASE}\`.* TO '${TINYHAT_RUNTIME_USER}'@'%';
${MIGRATION_GRANT}
FLUSH PRIVILEGES;
SQL
