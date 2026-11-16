package com.hexora.hexora.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/**
 * Concede a la cuenta de ejecución sus permisos, tabla por tabla, usando la conexión de la cuenta de migración.
 * Es la versión automática de {@code hexora/database/grant-runtime-privileges.sql}:
 * <ul>
 *   <li>cada tabla de negocio: SELECT, INSERT, UPDATE, DELETE;</li>
 *   <li>las tablas "solo agregar" (por defecto {@code audit_event}): SELECT e INSERT;</li>
 *   <li>{@code flyway_schema_history}: sin acceso;</li>
 *   <li>sin DDL ni GRANT OPTION.</li>
 * </ul>
 * Es idempotente: antes de conceder revoca lo que hubiera en la base o en la tabla, de modo que el resultado
 * no depende de permisos anteriores (por ejemplo, el permiso a nivel de base que crea el script de inicio).
 *
 * <p>La cuenta de migración necesita GRANT OPTION sobre la base. Las sentencias GRANT/REVOKE no admiten
 * parámetros; por eso el usuario, el host y la base se validan con una lista cerrada de caracteres y los
 * nombres de tabla (que salen de information_schema) se escapan.
 */
public final class RuntimePrivilegeGranter {

    static final String HISTORY_TABLE = "flyway_schema_history";

    private static final Pattern USER = Pattern.compile("[A-Za-z0-9_.-]{1,32}");
    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9_.%:-]{1,255}");
    private static final Pattern SCHEMA = Pattern.compile("[A-Za-z0-9_$]{1,64}");
    /** Un GRANT a nivel de base ({@code ON `base`.*}); los de tabla llevan {@code `base`.`tabla`} y los globales {@code *.*}. */
    private static final Pattern DATABASE_LEVEL = Pattern.compile(" ON `[^`]+`\\.\\* TO ");
    /** Privilegios de tabla que el usuario de ejecución nunca debe tener (todo lo que no es DML permitido). */
    private static final String BUSINESS_EXCESS = "ALTER, CREATE, CREATE VIEW, DROP, GRANT OPTION, INDEX, REFERENCES, SHOW VIEW, TRIGGER";
    private static final String APPEND_ONLY_EXCESS = "UPDATE, DELETE, " + BUSINESS_EXCESS;
    /** ER_NONEXISTING_GRANT y ER_NONEXISTING_TABLE_GRANT: revocar algo que no estaba concedido no es un error. */
    private static final Set<Integer> NO_SUCH_GRANT = Set.of(1141, 1147);

    private final String account;
    private final Set<String> appendOnlyTables;

    public RuntimePrivilegeGranter(String runtimeUser, String host, Set<String> appendOnlyTables) {
        if (runtimeUser == null || !USER.matcher(runtimeUser).matches()) {
            throw new IllegalArgumentException("Nombre de usuario de ejecución no válido para conceder permisos");
        }
        if (host == null || !HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("Host del usuario de ejecución no válido para conceder permisos");
        }
        this.account = "'" + runtimeUser + "'@'" + host + "'";
        this.appendOnlyTables = Set.copyOf(appendOnlyTables);
    }

    public Result apply(DataSource adminDataSource) {
        try (Connection connection = adminDataSource.getConnection()) {
            String schema = connection.getCatalog();
            if (schema == null || !SCHEMA.matcher(schema).matches()) {
                throw new RuntimePrivilegeException("No se pudo determinar un nombre de base válido", null);
            }
            List<String> tables = listTables(connection, schema);
            List<String> appendOnlyFound = new ArrayList<>();
            try (Statement statement = connection.createStatement()) {
                // 1) Primero solo se AÑADE. Si la cuenta de migración no puede conceder, el primer GRANT falla y
                //    no se ha quitado nada: el usuario de ejecución conserva los permisos que ya tenía.
                for (String table : tables) {
                    boolean appendOnly = isAppendOnly(table);
                    statement.execute("GRANT " + (appendOnly ? "SELECT, INSERT" : "SELECT, INSERT, UPDATE, DELETE")
                        + " ON " + quote(schema) + "." + quote(table) + " TO " + account);
                    if (appendOnly) {
                        appendOnlyFound.add(table);
                    }
                }
                // 2) Se retira el permiso a nivel de base (el que crea el script de inicio): taparía las
                //    restricciones por tabla. Se nombra con "_" escapado, que es como lo concede el script y la única
                //    forma que la cuenta de migración puede revocar (ver 01-runtime-user.sh). Un permiso concedido con
                //    el nombre sin escapar no se puede retirar desde aquí: lo detecta databaseLevelGrants().
                revoke(statement, "REVOKE ALL PRIVILEGES ON " + quote(schema.replace("_", "\\_")) + ".* FROM " + account);
                // 3) Se retira de cada tabla todo lo que sobre, vengan los permisos de donde vengan.
                for (String table : tables) {
                    revoke(statement, "REVOKE " + (isAppendOnly(table) ? APPEND_ONLY_EXCESS : BUSINESS_EXCESS)
                        + " ON " + quote(schema) + "." + quote(table) + " FROM " + account);
                }
            }
            return new Result(schema, tables.size(), List.copyOf(appendOnlyFound));
        } catch (SQLException exception) {
            throw new RuntimePrivilegeException(
                "No se pudieron conceder los permisos (código MySQL " + exception.getErrorCode() + ")", exception);
        }
    }

    /**
     * Permisos a nivel de base que conserva la cuenta con la que se abre {@code runtimeDataSource} (normalmente la
     * cuenta de ejecución de la aplicación), vistos desde esa propia cuenta con {@code SHOW GRANTS}. La cuenta de
     * migración no puede verlos ni, si se concedieron con el nombre de base sin escapar, retirarlos; por eso se
     * comprueba desde el lado de la ejecución. Un permiso de base tapa las restricciones por tabla (por ejemplo,
     * dejaría a {@code audit_event} con UPDATE y DELETE). Lista vacía = estado correcto.
     */
    public static List<String> databaseLevelGrants(DataSource runtimeDataSource) {
        List<String> found = new ArrayList<>();
        try (Connection connection = runtimeDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SHOW GRANTS")) {
            while (rows.next()) {
                String grant = rows.getString(1);
                if (!grant.startsWith("GRANT USAGE") && DATABASE_LEVEL.matcher(grant).find()) {
                    found.add(grant.replaceAll(" TO .*", ""));
                }
            }
        } catch (SQLException exception) {
            throw new RuntimePrivilegeException(
                "No se pudieron comprobar los permisos del usuario de ejecución (código MySQL "
                    + exception.getErrorCode() + ")", exception);
        }
        return found;
    }

    private List<String> listTables(Connection connection, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(
            "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name <> ? ORDER BY table_name")) {
            query.setString(1, schema);
            query.setString(2, HISTORY_TABLE);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    tables.add(rows.getString(1));
                }
            }
        }
        return tables;
    }

    private boolean isAppendOnly(String table) {
        return appendOnlyTables.contains(table.toLowerCase(Locale.ROOT));
    }

    private static void revoke(Statement statement, String sql) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException exception) {
            if (!NO_SUCH_GRANT.contains(exception.getErrorCode())) {
                throw exception;
            }
        }
    }

    private static String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    public record Result(String schema, int tablesGranted, List<String> appendOnlyTables) {
    }

    /** Falla al conceder permisos; el mensaje no incluye credenciales. */
    public static final class RuntimePrivilegeException extends RuntimeException {
        public RuntimePrivilegeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
