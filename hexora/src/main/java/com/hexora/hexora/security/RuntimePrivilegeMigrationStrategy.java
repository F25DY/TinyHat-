package com.hexora.hexora.security;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Sustituye a la estrategia por defecto de Flyway: migra y, a continuación, concede al usuario de ejecución sus
 * permisos con la conexión de migración (ver {@link RuntimePrivilegeGranter}). Así no hay que acordarse de ejecutar
 * {@code grant-runtime-privileges.sql} tras cada migración que cree tablas. Después comprueba, desde la propia cuenta
 * de ejecución, que no le quede ningún permiso a nivel de base (taparía la restricción de solo agregar).
 *
 * <p>No hace nada si el usuario de ejecución y el de migración son el mismo (entornos de un solo usuario) o si
 * {@code tinyhat.db.runtime-grants.enabled=false}. Si algo no se puede garantizar: en {@code prod} el arranque se
 * detiene (mejor no arrancar que arrancar con permisos sin aplicar); en otros perfiles se registra un error con el
 * comando que lo corrige y la aplicación continúa. Un fallo al conceder no retira ningún permiso existente.
 */
@Component
public class RuntimePrivilegeMigrationStrategy implements FlywayMigrationStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(RuntimePrivilegeMigrationStrategy.class);

    private final Environment environment;
    private final ObjectProvider<DataSource> runtimeDataSource;

    public RuntimePrivilegeMigrationStrategy(Environment environment, ObjectProvider<DataSource> runtimeDataSource) {
        this.environment = environment;
        this.runtimeDataSource = runtimeDataSource;
    }

    @Override
    public void migrate(Flyway flyway) {
        flyway.migrate();

        if (!environment.getProperty("tinyhat.db.runtime-grants.enabled", Boolean.class, true)) {
            LOGGER.info("Concesión automática de permisos desactivada (tinyhat.db.runtime-grants.enabled=false)");
            return;
        }
        String runtimeUser = environment.getProperty("spring.datasource.username", "");
        String migrationUser = environment.getProperty("spring.flyway.user", "");
        if (runtimeUser.isBlank() || runtimeUser.equals(migrationUser)) {
            LOGGER.info("Usuario de ejecución y de migración son la misma cuenta: no se aplican permisos por tabla");
            return;
        }

        try {
            RuntimePrivilegeGranter.Result result = new RuntimePrivilegeGranter(
                runtimeUser,
                environment.getProperty("tinyhat.db.runtime-grants.host", "%"),
                appendOnlyTables()
            ).apply(flyway.getConfiguration().getDataSource());
            LOGGER.info(
                "Permisos del usuario de ejecución aplicados en {} tablas de {} (solo agregar: {})",
                result.tablesGranted(), result.schema(), result.appendOnlyTables());
        } catch (RuntimePrivilegeGranter.RuntimePrivilegeException exception) {
            report(exception.getMessage()
                + ". La cuenta de migración necesita GRANT OPTION sobre la base: como administrador, "
                + "GRANT ALL PRIVILEGES ON `<base>`.* TO '<cuenta-de-migración>'@'%' WITH GRANT OPTION; "
                + "(si el nombre de la base lleva '_', escríbelo escapado, por ejemplo `hexora\\_db`, igual que "
                + "aparece en SHOW GRANTS; sin escapar MySQL lo ignora). "
                + "Los permisos que ya tenía el usuario de ejecución no se han modificado. "
                + "Alternativa: desactiva tinyhat.db.runtime-grants.enabled y ejecuta "
                + "hexora/database/grant-runtime-privileges.sql a mano.", exception);
            return;
        }

        verifyNoDatabaseLevelGrant(runtimeUser);
    }

    private void verifyNoDatabaseLevelGrant(String runtimeUser) {
        DataSource dataSource = runtimeDataSource.getIfAvailable();
        if (dataSource == null) {
            return;
        }
        try {
            List<String> remaining = RuntimePrivilegeGranter.databaseLevelGrants(dataSource);
            if (!remaining.isEmpty()) {
                report("El usuario de ejecución conserva permisos a nivel de base que la cuenta de migración no puede "
                    + "retirar: " + remaining + ". Suelen venir de un GRANT con el nombre de base sin escapar. "
                    + "Mientras existan, la restricción de solo agregar de audit_event no está garantizada. "
                    + "Como administrador: REVOKE ALL PRIVILEGES ON `<base>`.* FROM '" + runtimeUser + "'@'%';",
                    null);
            }
        } catch (RuntimePrivilegeGranter.RuntimePrivilegeException exception) {
            report(exception.getMessage(), exception);
        }
    }

    /** En prod detiene el arranque; en el resto registra un error y continúa. */
    private void report(String message, Exception cause) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException(message, cause);
        }
        LOGGER.error(message);
    }

    private Set<String> appendOnlyTables() {
        return Arrays.stream(environment.getProperty("tinyhat.db.runtime-grants.append-only-tables", "audit_event")
                .split(","))
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }
}
