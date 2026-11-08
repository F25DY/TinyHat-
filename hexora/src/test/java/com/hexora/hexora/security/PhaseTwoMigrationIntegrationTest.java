package com.hexora.hexora.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
	"spring.jpa.hibernate.ddl-auto=validate",
	"spring.flyway.clean-disabled=true",
	"spring.flyway.target=23",
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=2147483647",
	"hexora.network.retention-cleanup-interval-ms=2147483647"
})
class PhaseTwoMigrationIntegrationTest {

	private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
		.withDatabaseName("hexora_phase2")
		.withUsername("hexora_test")
		.withPassword("hexora_test_password");

	private static final Map<String, Long> PRE_MIGRATION_COUNTS = new LinkedHashMap<>();

	static {
		MYSQL.start();
		JdbcTemplate database = new JdbcTemplate(new DriverManagerDataSource(
			MYSQL.getJdbcUrl(),
			MYSQL.getUsername(),
			MYSQL.getPassword()
		));

		Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion("17"))
			.load()
			.migrate();

		Map<String, String> preMigrationTableNames = Map.ofEntries(
			Map.entry("empresa", "organizacion_cliente"),
			Map.entry("auditoria", "auditoria"),
			Map.entry("area_auditable", "area_auditable"),
			Map.entry("vulnerabilidad", "vulnerabilidad"),
			Map.entry("network_host", "network_host"),
			Map.entry("network_scan", "network_scan"),
			Map.entry("port_scan_result", "port_scan_result"),
			Map.entry("network_event", "network_event"),
			Map.entry("monitored_port", "monitored_port"),
			Map.entry("network_detected_device", "network_detected_device"),
			Map.entry("network_discovery_event", "network_discovery_event"),
			Map.entry("network_discovery_snapshot", "network_discovery_snapshot"),
			Map.entry("network_discovery_snapshot_device", "network_discovery_snapshot_device")
		);
		preMigrationTableNames.forEach((source, target) ->
			PRE_MIGRATION_COUNTS.put(target, database.queryForObject(
				"SELECT COUNT(*) FROM " + source,
				Long.class
			))
		);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private CapabilityService capabilityService;

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
		registry.add("spring.flyway.user", MYSQL::getUsername);
		registry.add("spring.flyway.password", MYSQL::getPassword);
	}

	@AfterAll
	static void stopDatabase() {
		MYSQL.stop();
	}

	@Test
	void migratesToVersion23WithoutLosingExistingRowsOrPublicIds() {
		assertThat(jdbcTemplate.queryForObject(
			"SELECT version FROM flyway_schema_history WHERE success = TRUE " +
				"ORDER BY installed_rank DESC LIMIT 1",
			String.class
		)).isEqualTo("23");

		PRE_MIGRATION_COUNTS.forEach((table, expectedCount) ->
			assertThat(countRows(table)).as("row count in %s", table).isEqualTo(expectedCount)
		);

		List<String> publicIdTables = List.of(
			"cuenta",
			"organizacion_cliente",
			"auditoria",
			"area_auditable",
			"vulnerabilidad",
			"usuario",
			"informe",
			"network_host",
			"network_scan",
			"port_scan_result",
			"network_event",
			"monitored_port",
			"network_detected_device",
			"network_discovery_event",
			"network_discovery_snapshot",
			"network_discovery_snapshot_device"
		);
		for (String table : publicIdTables) {
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM " + table + " WHERE public_id IS NULL",
				Long.class
			)).as("null public IDs in %s", table).isZero();
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) - COUNT(DISTINCT public_id) FROM " + table,
				Long.class
			)).as("duplicate public IDs in %s", table).isZero();
		}
	}

	@Test
	void enablesAllCapabilitiesForInitialAccount() {
		for (Capability capability : Capability.values()) {
			assertThat(capabilityService.isEnabled(1L, capability))
				.as("initial account capability %s", capability)
				.isTrue();
		}
	}

	@Test
	@Transactional
	void rejectsNetworkScanReferencingHostFromAnotherAccount() {
		jdbcTemplate.update(
			"INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) " +
				"VALUES (UUID(), 'Cuenta de prueba', 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))"
		);
		Long otherAccountId = jdbcTemplate.queryForObject(
			"SELECT id FROM cuenta WHERE nombre = 'Cuenta de prueba'",
			Long.class
		);
		jdbcTemplate.update(
			"INSERT INTO network_host (public_id, display_name, address, status, enabled, cuenta_id) " +
				"VALUES (UUID(), 'Host de prueba', '192.0.2.10', 'UNKNOWN', FALSE, 1)"
		);
		Long hostId = jdbcTemplate.queryForObject(
			"SELECT id FROM network_host WHERE address = '192.0.2.10'",
			Long.class
		);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"INSERT INTO network_scan " +
				"(public_id, host_id, cuenta_id, status, host_status, ports_scanned, started_at, completed_at) " +
				"VALUES (UUID(), ?, ?, 'COMPLETED', 'UNKNOWN', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
			hostId,
			otherAccountId
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private long countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
	}
}
