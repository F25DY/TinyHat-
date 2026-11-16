package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Comprueba contra un MySQL real que la concesión automática de permisos deja exactamente lo que pedía la Fase 12:
 * DML por tabla, {@code audit_event} solo SELECT e INSERT, sin acceso a {@code flyway_schema_history} y sin DDL.
 *
 * <p>El contenedor usa el script de inicio real ({@code database/init/01-runtime-user.sh}), de modo que las cuentas
 * son las que crea Docker en un volumen nuevo: la de migración con {@code ALL} más {@code GRANT OPTION} y la de
 * ejecución solo con DML a nivel de base. Un fallo en ese script (por ejemplo, conceder {@code GRANT OPTION} sobre el
 * nombre de base sin escapar, que MySQL ignora) hace fallar estas pruebas. Todo con datos y cuentas ficticias.
 */
class RuntimePrivilegeGrantIntegrationTest {

    private static final String SCHEMA = "hexora_grants";
    private static final String PASSWORD = "admin_pw_test";
    private static final String RUNTIME = "rt_runtime";
    private static final String RUNTIME_PASSWORD = "runtime_pw_test";
    /** Cuenta de migración: la crea la imagen (MYSQL_USER) y el script de inicio le añade GRANT OPTION. */
    private static final String MIGRATION = "rt_migration";
    /** Cuenta de migración sin GRANT OPTION, como la deja la imagen de MySQL si no se usa el script de inicio. */
    private static final String PLAIN = "rt_plain";
    private static final String PLAIN_PASSWORD = "plain_pw_test";

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
        .withDatabaseName(SCHEMA)
        .withUsername(MIGRATION)
        .withPassword(PASSWORD)
        .withEnv("TINYHAT_RUNTIME_USER", RUNTIME)
        .withEnv("TINYHAT_RUNTIME_PASSWORD", RUNTIME_PASSWORD)
        .withCopyFileToContainer(
            MountableFile.forHostPath(Path.of("database", "init", "01-runtime-user.sh").toAbsolutePath(), 0755),
            "/docker-entrypoint-initdb.d/01-runtime-user.sh");

    @BeforeAll
    static void startAndMigrate() throws SQLException {
        MYSQL.start();
        try (Connection root = connect("root", PASSWORD); Statement statement = root.createStatement()) {
            // Igual que la imagen: ALL sobre la base con el nombre escapado y sin GRANT OPTION.
            statement.execute("CREATE USER '" + PLAIN + "'@'%' IDENTIFIED BY '" + PLAIN_PASSWORD + "'");
            statement.execute("GRANT ALL PRIVILEGES ON `" + SCHEMA.replace("_", "\\_") + "`.* TO '" + PLAIN + "'@'%'");
        }
        // Comprueba que el script de inicio hizo su trabajo antes de que cada prueba parta de ahí.
        assertThat(grantsOf(MIGRATION)).anyMatch(grant -> grant.contains("WITH GRANT OPTION"));
        assertThat(grantsOf(RUNTIME)).noneMatch(grant -> grant.contains("GRANT OPTION"));
        flyway(MIGRATION, PASSWORD).migrate();
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void grantsDmlPerTableKeepsAuditAppendOnlyAndBlocksDdl() throws SQLException {
        RuntimePrivilegeGranter.Result result = granter().apply(dataSource(MIGRATION, PASSWORD));

        Map<String, String> privileges = tablePrivilegesOfRuntimeUser();
        assertThat(privileges).hasSize(result.tablesGranted());
        assertThat(privileges.get("audit_event")).isEqualTo("INSERT,SELECT");
        assertThat(privileges.get("cuenta")).isEqualTo("DELETE,INSERT,SELECT,UPDATE");
        assertThat(privileges).doesNotContainKey("flyway_schema_history");
        assertThat(result.appendOnlyTables()).containsExactly("audit_event");
        assertThat(count("SELECT COUNT(*) FROM information_schema.schema_privileges WHERE grantee = \"'" + RUNTIME
            + "'@'%'\" AND table_schema = '" + SCHEMA + "'"))
            .as("el permiso a nivel de base que crea el script de inicio debe haberse retirado").isZero();
        assertThat(RuntimePrivilegeGranter.databaseLevelGrants(dataSource(RUNTIME, RUNTIME_PASSWORD)))
            .as("visto desde la propia cuenta de ejecución").isEmpty();

        try (Connection runtime = connect(RUNTIME, RUNTIME_PASSWORD)) {
            assertDoesNotThrow(() -> query(runtime, "SELECT COUNT(*) FROM cuenta"));
            assertDenied(runtime, "UPDATE audit_event SET id = id WHERE 1 = 0");
            assertDenied(runtime, "DELETE FROM audit_event WHERE 1 = 0");
            assertDenied(runtime, "SELECT COUNT(*) FROM flyway_schema_history");
            assertDenied(runtime, "CREATE TABLE ddl_check (id INT)");
            assertDenied(runtime, "ALTER TABLE cuenta ADD COLUMN ddl_check INT");
            assertDenied(runtime, "DROP TABLE cuenta");
            assertDenied(runtime, "TRUNCATE TABLE cuenta");
            assertDenied(runtime, "GRANT ALL ON `" + SCHEMA + "`.* TO '" + RUNTIME + "'@'%'");
        }
    }

    @Test
    void isIdempotentAndCoversTablesCreatedByLaterMigrations() throws SQLException {
        DataSource admin = dataSource(MIGRATION, PASSWORD);
        granter().apply(admin);
        granter().apply(admin);

        try (Connection adminConnection = admin.getConnection(); Statement statement = adminConnection.createStatement()) {
            statement.execute("CREATE TABLE tabla_migracion_nueva (id INT PRIMARY KEY)");
        }
        try (Connection runtime = connect(RUNTIME, RUNTIME_PASSWORD)) {
            assertDenied(runtime, "SELECT COUNT(*) FROM tabla_migracion_nueva");
        }

        granter().apply(admin);

        try (Connection runtime = connect(RUNTIME, RUNTIME_PASSWORD); Statement statement = runtime.createStatement()) {
            statement.execute("INSERT INTO tabla_migracion_nueva (id) VALUES (1)");
            assertThat(query(runtime, "SELECT COUNT(*) FROM tabla_migracion_nueva")).isEqualTo("1");
        }
        assertThat(tablePrivilegesOfRuntimeUser().get("tabla_migracion_nueva")).isEqualTo("DELETE,INSERT,SELECT,UPDATE");
    }

    @Test
    void removesPrivilegesLeftOverFromPreviousStates() throws SQLException {
        try (Connection root = connect("root", PASSWORD); Statement statement = root.createStatement()) {
            statement.execute("GRANT ALTER, DROP, UPDATE ON `" + SCHEMA + "`.`audit_event` TO '" + RUNTIME + "'@'%'");
            statement.execute("GRANT ALTER, INDEX ON `" + SCHEMA + "`.`cuenta` TO '" + RUNTIME + "'@'%'");
        }
        assertThat(tablePrivilegesOfRuntimeUser().get("audit_event")).contains("ALTER", "DROP", "UPDATE");

        granter().apply(dataSource(MIGRATION, PASSWORD));

        Map<String, String> privileges = tablePrivilegesOfRuntimeUser();
        assertThat(privileges.get("audit_event")).isEqualTo("INSERT,SELECT");
        assertThat(privileges.get("cuenta")).isEqualTo("DELETE,INSERT,SELECT,UPDATE");
    }

    @Test
    void aFailedAttemptLeavesTheExistingPrivilegesUntouched() throws SQLException {
        List<String> before = grantsOf(RUNTIME);

        RuntimePrivilegeGranter.RuntimePrivilegeException failure = assertThrows(
            RuntimePrivilegeGranter.RuntimePrivilegeException.class,
            () -> granter().apply(dataSource(PLAIN, PLAIN_PASSWORD)));

        assertThat(failure.getMessage()).contains("código MySQL");
        assertThat(failure.getMessage()).doesNotContain(PLAIN_PASSWORD).doesNotContain(RUNTIME_PASSWORD);
        assertThat(grantsOf(RUNTIME))
            .as("si la cuenta de migración no puede conceder, el usuario de ejecución no debe perder nada")
            .isEqualTo(before);
        try (Connection runtime = connect(RUNTIME, RUNTIME_PASSWORD)) {
            assertDoesNotThrow(() -> query(runtime, "SELECT COUNT(*) FROM cuenta"));
        }
    }

    @Test
    void strategyAppliesGrantsAfterMigrating() throws SQLException {
        MockEnvironment environment = environment(RUNTIME, MIGRATION);

        strategy(environment).migrate(flyway(MIGRATION, PASSWORD));

        assertThat(tablePrivilegesOfRuntimeUser().get("audit_event")).isEqualTo("INSERT,SELECT");
    }

    @Test
    void strategyStopsStartupInProdButOnlyWarnsElsewhere() throws SQLException {
        Flyway withoutGrantOption = flyway(PLAIN, PLAIN_PASSWORD);
        List<String> before = grantsOf(RUNTIME);

        MockEnvironment prod = environment(RUNTIME, PLAIN);
        prod.setActiveProfiles("prod");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> strategy(prod).migrate(withoutGrantOption));
        assertThat(failure.getMessage()).contains("WITH GRANT OPTION").contains("hexora\\_db");

        MockEnvironment dev = environment(RUNTIME, PLAIN);
        dev.setActiveProfiles("dev");
        assertDoesNotThrow(() -> strategy(dev).migrate(withoutGrantOption));
        assertThat(grantsOf(RUNTIME)).isEqualTo(before);
    }

    @Test
    void strategyDoesNothingWhenDisabledOrWhenBothAccountsAreTheSame() {
        Flyway withoutGrantOption = flyway(PLAIN, PLAIN_PASSWORD);

        MockEnvironment disabled = environment(RUNTIME, PLAIN);
        disabled.setActiveProfiles("prod");
        disabled.setProperty("tinyhat.db.runtime-grants.enabled", "false");
        assertDoesNotThrow(() -> strategy(disabled).migrate(withoutGrantOption));

        MockEnvironment sameAccount = environment(PLAIN, PLAIN);
        sameAccount.setActiveProfiles("prod");
        assertDoesNotThrow(() -> strategy(sameAccount).migrate(withoutGrantOption));
    }

    @Test
    void detectsDatabaseLevelPrivilegesTheMigrationAccountCannotRevoke() throws SQLException {
        // Un GRANT a nivel de base escrito con el nombre SIN escapar (como el script de la Fase 11): la cuenta de
        // migración no puede retirarlo y taparía la restricción de solo agregar de audit_event.
        try (Connection root = connect("root", PASSWORD); Statement statement = root.createStatement()) {
            statement.execute("GRANT SELECT, UPDATE ON `" + SCHEMA + "`.* TO '" + RUNTIME + "'@'%'");
        }
        try {
            assertDoesNotThrow(() -> granter().apply(dataSource(MIGRATION, PASSWORD)));

            List<String> remaining = RuntimePrivilegeGranter.databaseLevelGrants(dataSource(RUNTIME, RUNTIME_PASSWORD));
            assertThat(remaining).singleElement().asString().contains("SELECT, UPDATE").contains(SCHEMA);

            MockEnvironment prod = environment(RUNTIME, MIGRATION);
            prod.setActiveProfiles("prod");
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> strategy(prod).migrate(flyway(MIGRATION, PASSWORD)));
            assertThat(failure.getMessage()).contains("REVOKE ALL PRIVILEGES").contains("audit_event");

            MockEnvironment dev = environment(RUNTIME, MIGRATION);
            dev.setActiveProfiles("dev");
            assertDoesNotThrow(() -> strategy(dev).migrate(flyway(MIGRATION, PASSWORD)));
        } finally {
            try (Connection root = connect("root", PASSWORD); Statement statement = root.createStatement()) {
                statement.execute("REVOKE ALL PRIVILEGES ON `" + SCHEMA + "`.* FROM '" + RUNTIME + "'@'%'");
            }
        }
        assertThat(RuntimePrivilegeGranter.databaseLevelGrants(dataSource(RUNTIME, RUNTIME_PASSWORD))).isEmpty();
    }

    @Test
    void rejectsAccountNamesThatCouldInjectSql() {
        Set<String> none = Set.of("audit_event");
        assertThrows(IllegalArgumentException.class, () -> new RuntimePrivilegeGranter("x'; DROP TABLE cuenta; --", "%", none));
        assertThrows(IllegalArgumentException.class, () -> new RuntimePrivilegeGranter(RUNTIME, "%' OR '1'='1", none));
        assertThrows(IllegalArgumentException.class, () -> new RuntimePrivilegeGranter("", "%", none));
        assertThrows(IllegalArgumentException.class, () -> new RuntimePrivilegeGranter(null, "%", none));
    }

    // --- utilidades ---

    /** La estrategia con la cuenta de ejecución como "DataSource de la aplicación" (para la comprobación final). */
    private static RuntimePrivilegeMigrationStrategy strategy(MockEnvironment environment) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("runtimeDataSource", dataSource(RUNTIME, RUNTIME_PASSWORD));
        return new RuntimePrivilegeMigrationStrategy(environment, beans.getBeanProvider(DataSource.class));
    }

    private static RuntimePrivilegeGranter granter() {
        return new RuntimePrivilegeGranter(RUNTIME, "%", Set.of("audit_event"));
    }

    private static MockEnvironment environment(String runtimeUser, String migrationUser) {
        return new MockEnvironment()
            .withProperty("spring.datasource.username", runtimeUser)
            .withProperty("spring.flyway.user", migrationUser);
    }

    private static Flyway flyway(String user, String password) {
        return Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), user, password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("2")
            .load();
    }

    private static DataSource dataSource(String user, String password) {
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), user, password);
    }

    private static Connection connect(String user, String password) throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), user, password);
    }

    private static List<String> grantsOf(String user) throws SQLException {
        List<String> grants = new ArrayList<>();
        try (Connection root = connect("root", PASSWORD);
             Statement statement = root.createStatement();
             ResultSet rows = statement.executeQuery("SHOW GRANTS FOR '" + user + "'@'%'")) {
            while (rows.next()) {
                grants.add(rows.getString(1).replaceAll("IDENTIFIED.*", "").trim());
            }
        }
        grants.sort(String::compareTo);
        return grants;
    }

    private static Map<String, String> tablePrivilegesOfRuntimeUser() throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (Connection root = connect("root", PASSWORD);
             Statement statement = root.createStatement();
             ResultSet rows = statement.executeQuery(
                 "SELECT table_name, GROUP_CONCAT(privilege_type ORDER BY privilege_type) "
                     + "FROM information_schema.table_privileges "
                     + "WHERE grantee = \"'" + RUNTIME + "'@'%'\" AND table_schema = '" + SCHEMA + "' "
                     + "GROUP BY table_name")) {
            while (rows.next()) {
                result.put(rows.getString(1), rows.getString(2));
            }
        }
        return result;
    }

    private static int count(String sql) throws SQLException {
        try (Connection root = connect("root", PASSWORD)) {
            return Integer.parseInt(query(root, sql));
        }
    }

    private static String query(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    /** MySQL 1142 (comando denegado sobre la tabla) o 1044/1045 (acceso denegado a la base). */
    private static void assertDenied(Connection connection, String sql) {
        SQLException failure = assertThrows(SQLException.class, () -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }, "debía estar denegado: " + sql);
        assertThat(failure.getErrorCode()).as(sql).isIn(1142, 1044, 1045, 1227);
    }
}
