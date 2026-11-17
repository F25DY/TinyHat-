package com.hexora.hexora.riesgo;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexora.test.IsolatedMySqlTestDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Garantiza que la migración V30 (SQL) y {@link EscalaRiesgo} (Java) calculan lo mismo: así el nivel que muestra la
 * aplicación para datos antiguos es el mismo que se calcularía hoy al guardar. Todo ocurre dentro de una
 * transacción que se revierte, sin dejar filas en la base compartida.
 */
@SpringBootTest(properties = {
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=3600000",
	"hexora.network.discovery-interval-ms=3600000",
	"hexora.network.retention-cleanup-interval-ms=3600000",
	"tinyhat.security.mfa.required=false"
})
class EscalaRiesgoMigracionIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final String MIGRACION = "db/migration/V30__align_risk_level_with_report_scale.sql";
	private static final String MARCA = "migracion-v30";

	@Autowired private JdbcTemplate jdbc;

	@Test
	void elSqlDeLaMigracionCoincideConJavaEnTodasLasCombinaciones() {
		List<String> sentencias = sentenciasDeLaMigracion();
		assertThat(sentencias).hasSize(2);

		Map<String, int[]> casos = new LinkedHashMap<>();
		for (int maximo = 0; maximo <= 10; maximo++) {
			for (int posicion = 0; posicion < 3; posicion++) {
				for (int probabilidad = 0; probabilidad <= 10; probabilidad++) {
					int[] cia = {0, 0, 0};
					cia[posicion] = maximo;
					casos.put(UUID.randomUUID().toString(), new int[] {cia[0], cia[1], cia[2], probabilidad});
				}
			}
		}

		Map<String, String[]> obtenido = jdbc.execute((ConnectionCallback<Map<String, String[]>>) conexion -> {
			boolean autoCommit = conexion.getAutoCommit();
			conexion.setAutoCommit(false);
			try {
				ejecutar(conexion, "SET FOREIGN_KEY_CHECKS = 0");
				insertar(conexion, casos);
				for (String sentencia : sentencias) {
					ejecutar(conexion, sentencia);
				}
				return leer(conexion);
			} finally {
				conexion.rollback();
				ejecutar(conexion, "SET FOREIGN_KEY_CHECKS = 1");
				conexion.setAutoCommit(autoCommit);
			}
		});

		assertThat(obtenido).hasSameSizeAs(casos);
		casos.forEach((id, v) -> {
			String[] fila = obtenido.get(id);
			String descripcion = "C=" + v[0] + " I=" + v[1] + " D=" + v[2] + " P=" + v[3];
			assertThat(fila[1]).as("nivel " + descripcion)
				.isEqualTo(EscalaRiesgo.nivelDeVulnerabilidad(v[0], v[1], v[2], v[3]).valorAlmacenado());
			assertThat(Double.parseDouble(fila[0])).as("impacto " + descripcion)
				.isEqualTo((double) EscalaRiesgo.impactoGuardado(v[0], v[1], v[2]));
		});
	}

	private static void ejecutar(Connection conexion, String sql) throws SQLException {
		try (Statement sentencia = conexion.createStatement()) {
			sentencia.execute(sql);
		}
	}

	private static void insertar(Connection conexion, Map<String, int[]> casos) throws SQLException {
		String insercion = "INSERT INTO vulnerabilidad (confidencialidad, integridad, disponibilidad, probabilidad, "
			+ "impacto, nivel_riesgo, nombre, tipo, estado, fecha_descubrimiento, id_area, cuenta_id, public_id) "
			+ "VALUES (?, ?, ?, ?, 0, 'pendiente', '" + MARCA + "', 'prueba', 'Abierta', '2026-01-01', 1, 1, ?)";
		try (PreparedStatement lote = conexion.prepareStatement(insercion)) {
			for (Map.Entry<String, int[]> caso : casos.entrySet()) {
				int[] v = caso.getValue();
				lote.setInt(1, v[0]);
				lote.setInt(2, v[1]);
				lote.setInt(3, v[2]);
				lote.setInt(4, v[3]);
				lote.setString(5, caso.getKey());
				lote.addBatch();
			}
			lote.executeBatch();
		}
	}

	private static Map<String, String[]> leer(Connection conexion) throws SQLException {
		Map<String, String[]> resultado = new LinkedHashMap<>();
		try (Statement consulta = conexion.createStatement();
			 ResultSet filas = consulta.executeQuery(
				 "SELECT public_id, impacto, nivel_riesgo FROM vulnerabilidad WHERE nombre = '" + MARCA + "'")) {
			while (filas.next()) {
				resultado.put(filas.getString(1), new String[] {filas.getString(2), filas.getString(3)});
			}
		}
		return resultado;
	}

	private static List<String> sentenciasDeLaMigracion() {
		try (InputStream entrada = new ClassPathResource(MIGRACION).getInputStream()) {
			String texto = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
			StringBuilder sinComentarios = new StringBuilder();
			for (String linea : texto.split("\\R")) {
				if (!linea.strip().startsWith("--")) {
					sinComentarios.append(linea).append('\n');
				}
			}
			List<String> sentencias = new ArrayList<>();
			for (String sentencia : sinComentarios.toString().split(";")) {
				if (!sentencia.isBlank()) {
					sentencias.add(sentencia.strip());
				}
			}
			return sentencias;
		} catch (IOException excepcion) {
			throw new IllegalStateException("No se pudo leer la migración V30", excepcion);
		}
	}
}
