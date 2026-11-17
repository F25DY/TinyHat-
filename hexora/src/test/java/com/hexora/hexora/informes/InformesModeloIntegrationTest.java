package com.hexora.hexora.informes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.model.AsignacionAuditorProyecto;
import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Informe;
import com.hexora.hexora.model.InformeArchivo;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.AsignacionAuditorProyectoRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.PermissionService;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Modelo de informes sobre MySQL real: esquema, restricciones, invariantes I1 a I5, serialización JSON, bloqueo
 * optimista y aislamiento por cuenta, organización y rol en los repositorios.
 */
@SpringBootTest(properties = {
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=3600000",
	"hexora.network.discovery-interval-ms=3600000",
	"hexora.network.retention-cleanup-interval-ms=3600000",
	"tinyhat.security.mfa.required=false"
})
class InformesModeloIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final String SHA = "b".repeat(64);
	private static final String NARRATIVA = "{\"resumenEjecutivo\":\"Resumen con ñ y tildes áéíóú ✓\"}";

	@Autowired private JdbcTemplate jdbc;
	@Autowired private TenantTaskExecutor tenants;
	@Autowired private EmpresaRepository empresaRepository;
	@Autowired private AuditoriaRepository auditoriaRepository;
	@Autowired private AsignacionAuditorProyectoRepository asignaciones;
	@Autowired private UsuarioRepository usuarioRepository;
	@Autowired private InformeRepository informes;
	@Autowired private InformeVersionRepository versiones;
	@Autowired private InformeArchivoRepository archivos;
	@Autowired private PermissionService permissionService;

	// ---------------------------------------------------------------- esquema

	@Test
	void elEsquemaTieneLasTablasIndicesYRestriccionesDelPlan() {
		List<String> tablas = jdbc.queryForList(
			"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
				+ "AND table_name IN ('informe', 'informe_version', 'informe_archivo')", String.class);
		assertThat(tablas).containsExactlyInAnyOrder("informe", "informe_version", "informe_archivo");

		Set<String> indices = Set.copyOf(jdbc.queryForList(
			"SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
				+ "AND table_name IN ('informe', 'informe_version', 'informe_archivo') AND non_unique = 0",
			String.class));
		assertThat(indices).contains(
			"uk_informe_public_id", "uk_informe_proyecto_tipo", "uk_informe_id_cuenta",
			"uk_informe_version_public_id", "uk_informe_version_numero",
			"uk_informe_version_un_borrador", "uk_informe_version_un_publicado",
			"uk_informe_archivo_public_id", "uk_informe_archivo_version_formato");

		Set<String> restricciones = Set.copyOf(jdbc.queryForList(
			"SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = DATABASE() "
				+ "AND constraint_type = 'CHECK' AND table_name IN ('informe', 'informe_version', 'informe_archivo')",
			String.class));
		assertThat(restricciones).contains(
			"chk_informe_tipo", "chk_informe_alcance", "chk_informe_version_estado", "chk_informe_version_borrador",
			"chk_informe_version_publicado", "chk_informe_version_despublicado", "chk_informe_archivo_formato");

		Set<String> claves = Set.copyOf(jdbc.queryForList(
			"SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = DATABASE() "
				+ "AND constraint_type = 'FOREIGN KEY' AND table_name IN ('informe', 'informe_version', 'informe_archivo')",
			String.class));
		assertThat(claves).contains(
			"fk_informe_proyecto_organizacion_cuenta", "fk_informe_creado_por_cuenta",
			"fk_informe_version_informe_cuenta", "fk_informe_version_autor_cuenta", "fk_informe_archivo_version_cuenta");

		assertThat(jdbc.queryForObject(
			"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
				+ "AND table_name = 'informe' AND column_name IN ('contenido_json', 'hash_contenido', 'estado')",
			Integer.class)).as("las columnas del módulo anterior ya no existen").isZero();
		assertThat(jdbc.queryForObject(
			"SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() "
				+ "AND table_name = 'informe_version' AND column_name = 'narrativa_json'", String.class))
			.isEqualToIgnoringCase("json");
	}

	// ---------------------------------------------------------------- I1 e I2

	@Test
	void comoMaximoUnBorradorPorSerieI1() {
		Mundo m = mundo();
		long serie = serie(m, m.proyecto1, m.org1, TipoInforme.MIT, m.auditor1);
		tenants.execute(m.cuentaA, () -> versiones.saveAndFlush(
			new InformeVersion(serie, 1, m.auditor1.getUserId(), "{}")));

		assertThatThrownBy(() -> tenants.execute(m.cuentaA, () -> versiones.saveAndFlush(
			new InformeVersion(serie, 2, m.auditor1.getUserId(), "{}"))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void comoMaximoUnPublicadoPorSerieI2() {
		Mundo m = mundo();
		long serie = serie(m, m.proyecto1, m.org1, TipoInforme.FIN, m.auditor1);
		tenants.execute(m.cuentaA, () -> {
			InformeVersion v1 = versiones.saveAndFlush(new InformeVersion(serie, 1, m.auditor1.getUserId(), "{}"));
			v1.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(v1);
		});
		// Con v1 publicada puede haber un borrador v2, pero no publicarlo hasta despublicar v1.
		assertThatThrownBy(() -> tenants.execute(m.cuentaA, () -> {
			InformeVersion v2 = versiones.saveAndFlush(new InformeVersion(serie, 2, m.auditor1.getUserId(), "{}"));
			v2.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(v2);
		})).isInstanceOf(DataIntegrityViolationException.class);

		// Despublicada v1, v2 sí se puede publicar y conviven publicada y despublicada.
		tenants.execute(m.cuentaA, () -> {
			InformeVersion v1 = versiones.findByInformeIdAndCuentaIdOrderByNumeroDesc(serie, m.cuentaA).get(0);
			v1.despublicar("Se corrige la matriz de riesgo", LocalDateTime.now());
			versiones.saveAndFlush(v1);
			InformeVersion v2 = versiones.saveAndFlush(new InformeVersion(serie, 2, m.auditor1.getUserId(), "{}"));
			v2.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(v2);
		});
		assertThat(jdbc.queryForList("SELECT estado FROM informe_version WHERE informe_id = ? ORDER BY numero",
			String.class, serie)).containsExactly("DESPUBLICADO", "PUBLICADO");
	}

	// ---------------------------------------------------------------- restricciones de la base

	private static final String INSERT_VERSION = "INSERT INTO informe_version (public_id, cuenta_id, informe_id, numero, "
		+ "estado, narrativa_json, snapshot_json, snapshot_sha256, plantilla_version, autor_id, publicado_en, "
		+ "despublicado_en, motivo_despublicacion) VALUES (?, ?, ?, ?, ?, '{}', ?, ?, ?, ?, ?, ?, ?)";

	private static final String INSERT_INFORME = "INSERT INTO informe (public_id, cuenta_id, organizacion_id, "
		+ "proyecto_id, tipo, alcance, anio, creado_por) VALUES (?, ?, ?, ?, ?, ?, 2026, ?)";

	private static final String INSERT_ARCHIVO = "INSERT INTO informe_archivo (public_id, cuenta_id, version_id, formato, "
		+ "ruta_relativa, sha256, bytes, mime) VALUES (?, ?, ?, ?, 'a/b/c.bin', ?, 10, 'application/octet-stream')";

	@Test
	void laBaseRechazaLoQueNoCumpleLasRestricciones() {
		Mundo m = mundo();
		long cuenta = m.cuentaA;
		long autor = m.auditor2.getUserId();
		LocalDateTime t = LocalDateTime.now();
		long serie = serie(m, m.proyecto2, m.org2, TipoInforme.MIT, m.auditor2);

		// Control: una versión DESPUBLICADA completa es válida. Cada intento siguiente cambia un solo aspecto.
		jdbc.update(INSERT_VERSION, UUID.randomUUID().toString(), cuenta, serie, 1, "DESPUBLICADO",
			"{}", SHA, "2026.1", autor, t, t, "Motivo de prueba suficiente");

		String id = UUID.randomUUID().toString();
		// Estado inexistente.
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "OTRO", null, null, null, autor, null, null, null);
		// BORRADOR con snapshot.
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "BORRADOR", "{}", SHA, "2026.1", autor, null, null, null);
		// PUBLICADO sin snapshot, sin fecha de publicación, o con datos de despublicación.
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "PUBLICADO", null, null, null, autor, t, null, null);
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "PUBLICADO", "{}", SHA, "2026.1", autor, null, null, null);
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "PUBLICADO", "{}", SHA, "2026.1", autor, t, t, "Motivo suficiente");
		// DESPUBLICADO sin motivo o sin fecha de despublicación.
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "DESPUBLICADO", "{}", SHA, "2026.1", autor, t, t, null);
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "DESPUBLICADO", "{}", SHA, "2026.1", autor, t, null, "Motivo suficiente");
		// Número no positivo, número repetido y hash de longitud incorrecta.
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 0, "DESPUBLICADO", "{}", SHA, "2026.1", autor, t, t, "Motivo suficiente");
		rechaza(DUPLICADO, INSERT_VERSION, id, cuenta, serie, 1, "DESPUBLICADO", "{}", SHA, "2026.1", autor, t, t, "Motivo suficiente");
		rechaza(CHECK, INSERT_VERSION, id, cuenta, serie, 2, "DESPUBLICADO", "{}", "corto", "2026.1", autor, t, t, "Motivo suficiente");
		// Nada de lo anterior quedó guardado: solo existe la fila de control.
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe_version WHERE informe_id = ?", Integer.class, serie))
			.isEqualTo(1);

		// Serie: el control es válido; luego tipo inexistente, alcance no permitido y serie repetida.
		jdbc.update(INSERT_INFORME, UUID.randomUUID().toString(), cuenta, m.org2, m.proyecto2, "MR", "PROYECTO", autor);
		rechaza(CHECK, INSERT_INFORME, id, cuenta, m.org2, m.proyecto2, "XYZ", "PROYECTO", autor);
		rechaza(CHECK, INSERT_INFORME, id, cuenta, m.org2, m.proyecto2, "FIN", "AREA", autor);
		rechaza(DUPLICADO, INSERT_INFORME, id, cuenta, m.org2, m.proyecto2, "MR", "PROYECTO", autor);

		// Archivo: el control es válido (DOCX); luego formato inexistente, hash corto y formato repetido.
		jdbc.update(INSERT_ARCHIVO, UUID.randomUUID().toString(), cuenta, m.versionPublicadaTec1, "DOCX", SHA);
		rechaza(CHECK, INSERT_ARCHIVO, id, cuenta, m.versionPublicadaTec1, "ODT", SHA);
		rechaza(CHECK, INSERT_ARCHIVO, id, cuenta, m.versionPublicadaTec1, "XLSX", "corto");
		rechaza(DUPLICADO, INSERT_ARCHIVO, id, cuenta, m.versionPublicadaTec1, "DOCX", SHA);
	}

	@Test
	void laBaseImpideCruzarCuentasYOrganizaciones() {
		Mundo m = mundo();
		String id = UUID.randomUUID().toString();
		LocalDateTime t = LocalDateTime.now();

		// Control: serie válida de la cuenta A en su propio proyecto y organización.
		jdbc.update(INSERT_INFORME, UUID.randomUUID().toString(), m.cuentaA, m.org1, m.proyecto1, "MR", "PROYECTO",
			m.auditor1.getUserId());
		// Serie de la cuenta B apuntando a un proyecto de la cuenta A.
		rechaza(CLAVE_FORANEA, INSERT_INFORME, id, m.cuentaB, m.orgB, m.proyecto1, "MIT", "PROYECTO", m.auditorB.getUserId());
		// Organización equivocada para el proyecto (el proyecto 1 es de org1).
		rechaza(CLAVE_FORANEA, INSERT_INFORME, id, m.cuentaA, m.org2, m.proyecto1, "MIT", "PROYECTO", m.auditor1.getUserId());
		// Serie creada por un usuario de otra cuenta.
		rechaza(CLAVE_FORANEA, INSERT_INFORME, id, m.cuentaA, m.org1, m.proyecto1, "MIT", "PROYECTO", m.auditorB.getUserId());

		// Versión: el control es válido; la misma fila con la cuenta B sobre una serie de A, o con un autor de B, no.
		long serie = serie(m, m.proyecto2, m.org2, TipoInforme.FIN, m.auditor2);
		jdbc.update(INSERT_VERSION, UUID.randomUUID().toString(), m.cuentaA, serie, 1, "DESPUBLICADO",
			"{}", SHA, "2026.1", m.auditor2.getUserId(), t, t, "Motivo de prueba suficiente");
		rechaza(CLAVE_FORANEA, INSERT_VERSION, id, m.cuentaB, serie, 2, "DESPUBLICADO",
			"{}", SHA, "2026.1", m.auditorB.getUserId(), t, t, "Motivo de prueba suficiente");
		rechaza(CLAVE_FORANEA, INSERT_VERSION, id, m.cuentaA, serie, 2, "DESPUBLICADO",
			"{}", SHA, "2026.1", m.auditorB.getUserId(), t, t, "Motivo de prueba suficiente");

		// Archivo: el control es válido; el mismo con la cuenta B sobre una versión de la cuenta A, no.
		jdbc.update(INSERT_ARCHIVO, UUID.randomUUID().toString(), m.cuentaA, m.versionPublicadaTec1, "XLSX", SHA);
		rechaza(DISPARADOR, INSERT_ARCHIVO, id, m.cuentaB, m.versionPublicadaTec1, "DOCX", SHA);
	}

	// ---------------------------------------------------------------- disparadores (I3, I4, I5)

	@Test
	void unaVersionPublicadaNoSeEditaNiSeBorraEnLaBase() {
		Mundo m = mundo();
		rechaza(DISPARADOR, "UPDATE informe_version SET narrativa_json = '{}' WHERE public_id = ?", m.publicadaTec1);
		rechaza(DISPARADOR, "UPDATE informe_version SET snapshot_sha256 = ? WHERE public_id = ?", "c".repeat(64), m.publicadaTec1);
		rechaza(DISPARADOR, "UPDATE informe_version SET plantilla_version = '9.9' WHERE public_id = ?", m.publicadaTec1);
		rechaza(DISPARADOR, "UPDATE informe_version SET numero = 99 WHERE public_id = ?", m.publicadaTec1);
		rechaza(DISPARADOR, "DELETE FROM informe_version WHERE public_id = ?", m.publicadaTec1);
		assertThat(jdbc.queryForObject("SELECT estado FROM informe_version WHERE public_id = ?", String.class, m.publicadaTec1))
			.isEqualTo("PUBLICADO");
	}

	@Test
	void unaVersionPublicadaSoloPasaADespublicadaYNuncaVuelve() {
		Mundo m = mundo();
		// PUBLICADO -> BORRADOR no (I5); a DESPUBLICADO con motivo sí; y de ahí no se mueve (I3).
		rechaza(DISPARADOR, "UPDATE informe_version SET estado = 'BORRADOR' WHERE public_id = ?", m.publicadaProyecto2);
		jdbc.update("UPDATE informe_version SET estado = 'DESPUBLICADO', despublicado_en = UTC_TIMESTAMP(6), "
			+ "motivo_despublicacion = 'Motivo de prueba suficiente' WHERE public_id = ?", m.publicadaProyecto2);
		assertThat(jdbc.queryForObject("SELECT estado FROM informe_version WHERE public_id = ?", String.class, m.publicadaProyecto2))
			.isEqualTo("DESPUBLICADO");
		rechaza(DISPARADOR, "UPDATE informe_version SET motivo_despublicacion = 'Otro motivo distinto de antes' WHERE public_id = ?",
			m.publicadaProyecto2);
		rechaza(DISPARADOR, "UPDATE informe_version SET estado = 'PUBLICADO', despublicado_en = NULL, motivo_despublicacion = NULL "
			+ "WHERE public_id = ?", m.publicadaProyecto2);
		rechaza(DISPARADOR, "DELETE FROM informe_version WHERE public_id = ?", m.publicadaProyecto2);
		rechaza(DISPARADOR, "DELETE FROM informe_version WHERE public_id = ?", m.despublicadaTec1);
	}

	@Test
	void unBorradorSiSeEditaYSeBorra() {
		Mundo m = mundo();
		assertThat(jdbc.update("UPDATE informe_version SET narrativa_json = '{\"resumenEjecutivo\":\"x\"}' WHERE public_id = ?",
			m.borradorTec1)).isEqualTo(1);
		assertThat(jdbc.update("DELETE FROM informe_version WHERE public_id = ?", m.borradorEje1)).isEqualTo(1);
	}

	@Test
	void unArchivoSoloExisteParaVersionesPublicadasYNuncaCambia() {
		Mundo m = mundo();
		Long borrador = jdbc.queryForObject("SELECT id FROM informe_version WHERE public_id = ?", Long.class, m.borradorTec1);
		rechaza(DISPARADOR, INSERT_ARCHIVO, UUID.randomUUID().toString(), m.cuentaA, borrador, "XLSX", SHA);
		rechaza(DISPARADOR, "UPDATE informe_archivo SET sha256 = ? WHERE public_id = ?", "c".repeat(64), m.archivoPublicado);
		rechaza(DISPARADOR, "UPDATE informe_archivo SET ruta_relativa = 'otra/ruta.pdf' WHERE public_id = ?", m.archivoPublicado);
		rechaza(DISPARADOR, "DELETE FROM informe_archivo WHERE public_id = ?", m.archivoPublicado);
		rechaza(DISPARADOR, "DELETE FROM informe_archivo WHERE public_id = ?", m.archivoDespublicado);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe_archivo WHERE public_id IN (?, ?)", Integer.class,
			m.archivoPublicado, m.archivoDespublicado)).isEqualTo(2);
	}

	// ---------------------------------------------------------------- ciclo de vida en la base

	@Test
	void laNarrativaYElSnapshotSeGuardanComoJsonYSobrevivenConTildes() {
		Mundo m = mundo();
		Map<String, Object> fila = jdbc.queryForMap(
			"SELECT JSON_TYPE(narrativa_json) AS tipo_narrativa, "
				+ "JSON_UNQUOTE(JSON_EXTRACT(narrativa_json, '$.resumenEjecutivo')) AS resumen, "
				+ "JSON_UNQUOTE(JSON_EXTRACT(snapshot_json, '$.schemaVersion')) AS esquema, "
				+ "JSON_TYPE(snapshot_json) AS tipo_snapshot FROM informe_version WHERE public_id = ?",
			m.publicadaTec1);
		assertThat(fila.get("tipo_narrativa")).isEqualTo("OBJECT");
		assertThat(fila.get("tipo_snapshot")).isEqualTo("OBJECT");
		assertThat(fila.get("resumen")).isEqualTo("Resumen con ñ y tildes áéíóú ✓");
		assertThat(fila.get("esquema")).isEqualTo("1");

		tenants.execute(m.cuentaA, () -> {
			InformeVersion leida = versiones.findByPublicIdAndCuentaId(m.publicadaTec1, m.cuentaA).orElseThrow();
			assertThat(leida.getNarrativaJson()).contains("Resumen con ñ y tildes áéíóú ✓");
			assertThat(leida.getEstado()).isEqualTo(EstadoVersion.PUBLICADO);
			assertThat(leida.getSnapshotSha256()).isEqualTo(SHA);
			assertThat(leida.getPlantillaVersion()).isEqualTo("2026.1");
		});
	}

	@Test
	void dosEdicionesSimultaneasNoSePisanEnSilencio() {
		Mundo m = mundo();
		AtomicReference<InformeVersion> antigua = new AtomicReference<>();
		tenants.execute(m.cuentaA, () -> antigua.set(
			versiones.findByPublicIdAndCuentaId(m.borradorTec1, m.cuentaA).orElseThrow()));
		tenants.execute(m.cuentaA, () -> {
			InformeVersion actual = versiones.findByPublicIdAndCuentaId(m.borradorTec1, m.cuentaA).orElseThrow();
			actual.actualizarNarrativa("{\"conclusiones\":\"Primera edición\"}", LocalDateTime.now());
			versiones.saveAndFlush(actual);
		});
		antigua.get().actualizarNarrativa("{\"conclusiones\":\"Segunda edición\"}", LocalDateTime.now());
		assertThatThrownBy(() -> tenants.execute(m.cuentaA, () -> versiones.saveAndFlush(antigua.get())))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(jdbc.queryForObject(
			"SELECT JSON_UNQUOTE(JSON_EXTRACT(narrativa_json, '$.conclusiones')) FROM informe_version WHERE public_id = ?",
			String.class, m.borradorTec1)).isEqualTo("Primera edición");
	}

	@Test
	void loPublicadoNoSeEliminaYUnBorradorSiI3I5() {
		Mundo m = mundo();
		assertThatThrownBy(() -> tenants.execute(m.cuentaA, () -> versiones.delete(
			versiones.findByPublicIdAndCuentaId(m.publicadaTec1, m.cuentaA).orElseThrow())))
			.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> tenants.execute(m.cuentaA, () -> versiones.delete(
			versiones.findByPublicIdAndCuentaId(m.despublicadaTec1, m.cuentaA).orElseThrow())))
			.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe_version WHERE public_id IN (?, ?)",
			Integer.class, m.publicadaTec1, m.despublicadaTec1)).isEqualTo(2);

		tenants.execute(m.cuentaA, () -> versiones.delete(
			versiones.findByPublicIdAndCuentaId(m.borradorTec1, m.cuentaA).orElseThrow()));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe_version WHERE public_id = ?",
			Integer.class, m.borradorTec1)).isZero();
	}

	@Test
	void unNuevoBorradorPuedePrecargarLaNarrativaDeLaUltimaVersionI6() {
		Mundo m = mundo();
		long serie = serie(m, m.proyecto2, m.org2, TipoInforme.MR, m.auditor2);
		tenants.execute(m.cuentaA, () -> {
			InformeVersion v1 = versiones.saveAndFlush(new InformeVersion(serie, 1, m.auditor2.getUserId(), NARRATIVA));
			v1.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(v1);
		});
		tenants.execute(m.cuentaA, () -> {
			InformeVersion ultima = versiones.findFirstByInformeIdAndCuentaIdOrderByNumeroDesc(serie, m.cuentaA).orElseThrow();
			int siguiente = versiones.maxNumero(serie, m.cuentaA) + 1;
			assertThat(siguiente).isEqualTo(2);
			versiones.saveAndFlush(new InformeVersion(serie, siguiente, m.auditor2.getUserId(), ultima.getNarrativaJson()));
		});
		assertThat(jdbc.queryForObject(
			"SELECT JSON_UNQUOTE(JSON_EXTRACT(narrativa_json, '$.resumenEjecutivo')) FROM informe_version "
				+ "WHERE informe_id = ? AND numero = 2", String.class, serie))
			.isEqualTo("Resumen con ñ y tildes áéíóú ✓");
	}

	// ---------------------------------------------------------------- aislamiento

	@Test
	void unAuditorVeTodasLasVersionesDeSusProyectosYNadaMas() {
		Mundo m = mundo();
		for (String id : List.of(m.borradorTec1, m.publicadaTec1, m.despublicadaTec1, m.borradorEje1)) {
			assertThat(visible(id, m.auditor1)).as("auditor 1 ve " + id).isPresent();
			assertThat(visible(id, m.auditor2)).as("auditor 2 (otro proyecto) no ve " + id).isEmpty();
			assertThat(visible(id, m.auditorB)).as("auditor de otra cuenta no ve " + id).isEmpty();
		}
		assertThat(visible(m.publicadaProyecto2, m.auditor2)).isPresent();
		assertThat(visible(m.publicadaProyecto2, m.auditor1)).isEmpty();
		assertThat(visible(m.publicadaCuentaB, m.auditorB)).isPresent();
		assertThat(visible(m.publicadaCuentaB, m.auditor1)).isEmpty();
	}

	@Test
	void unClienteSoloVeLoPublicadoDeSuOrganizacion() {
		Mundo m = mundo();
		assertThat(visible(m.publicadaTec1, m.cliente1)).isPresent();
		assertThat(visible(m.borradorTec1, m.cliente1)).as("borrador").isEmpty();
		assertThat(visible(m.despublicadaTec1, m.cliente1)).as("despublicada").isEmpty();
		assertThat(visible(m.borradorEje1, m.cliente1)).as("borrador de otra serie").isEmpty();

		assertThat(visible(m.publicadaTec1, m.cliente2)).as("otra organización, misma cuenta").isEmpty();
		assertThat(visible(m.publicadaProyecto2, m.cliente1)).as("publicada de la organización 2").isEmpty();
		assertThat(visible(m.publicadaProyecto2, m.cliente2)).isPresent();
		assertThat(visible(m.publicadaTec1, m.clienteB)).as("otra cuenta").isEmpty();
		assertThat(visible(m.publicadaCuentaB, m.clienteB)).isPresent();
		assertThat(visible(m.publicadaCuentaB, m.cliente1)).isEmpty();
	}

	@Test
	void unRolDesconocidoNoVeNada() {
		Mundo m = mundo();
		assertThat(versiones.findVisibleByPublicId(m.publicadaTec1, m.cuentaA, m.org1, m.auditor1.getUserId(), "ADMIN"))
			.isEmpty();
		assertThat(versiones.findVisibleByPublicId(m.publicadaTec1, m.cuentaA, m.org1, m.auditor1.getUserId(), null))
			.isEmpty();
	}

	@Test
	void losListadosTambienSeFiltranEnLaConsulta() {
		Mundo m = mundo();
		List<InformeVersion> delAuditor = versiones.findVisibleByProyecto(
			m.proyecto1, m.cuentaA, m.org1, m.auditor1.getUserId(), "AUDITOR");
		assertThat(delAuditor).extracting(InformeVersion::getPublicId)
			.containsExactlyInAnyOrder(m.borradorTec1, m.publicadaTec1, m.despublicadaTec1, m.borradorEje1);

		assertThat(versiones.findVisibleByProyecto(m.proyecto1, m.cuentaA, m.org1, m.cliente1.getUserId(), "CLIENTE_ADMIN"))
			.extracting(InformeVersion::getPublicId).containsExactly(m.publicadaTec1);
		assertThat(versiones.findVisibleByProyecto(m.proyecto1, m.cuentaA, m.org2, m.cliente2.getUserId(), "CLIENTE_ADMIN"))
			.isEmpty();
		assertThat(versiones.findVisibleByProyecto(m.proyecto1, m.cuentaA, m.org1, m.auditor2.getUserId(), "AUDITOR"))
			.as("auditor sin asignación").isEmpty();
		assertThat(versiones.findVisibleByProyecto(m.proyecto1, m.cuentaB, m.orgB, m.auditorB.getUserId(), "AUDITOR"))
			.as("la cuenta B no alcanza el proyecto de A").isEmpty();

		assertThat(versiones.findPublicadasDeOrganizacion(m.cuentaA, m.org1))
			.extracting(InformeVersion::getPublicId).containsExactly(m.publicadaTec1);
		assertThat(versiones.findPublicadasDeOrganizacion(m.cuentaA, m.org2))
			.extracting(InformeVersion::getPublicId).containsExactly(m.publicadaProyecto2);
		assertThat(versiones.findPublicadasDeOrganizacion(m.cuentaB, m.org1))
			.as("misma organización, otra cuenta").isEmpty();
		assertThat(versiones.findPublicadasDeProyecto(m.cuentaA, m.proyecto1))
			.extracting(InformeVersion::getPublicId).containsExactly(m.publicadaTec1);
	}

	@Test
	void losArchivosSiguenLaMismaReglaDeVisibilidad() {
		Mundo m = mundo();
		assertThat(archivo(m.archivoPublicado, m.auditor1)).isPresent();
		assertThat(archivo(m.archivoPublicado, m.cliente1)).isPresent();
		assertThat(archivo(m.archivoPublicado, m.cliente2)).isEmpty();
		assertThat(archivo(m.archivoPublicado, m.auditor2)).isEmpty();
		assertThat(archivo(m.archivoPublicado, m.clienteB)).isEmpty();
		assertThat(archivo(m.archivoPublicado, m.auditorB)).isEmpty();

		assertThat(archivo(m.archivoDespublicado, m.auditor1)).as("el auditor sí ve lo despublicado").isPresent();
		assertThat(archivo(m.archivoDespublicado, m.cliente1)).as("el cliente no").isEmpty();
	}

	@Test
	void permissionServiceResponde404ParaLoQueNoSePuedeVer() {
		Mundo m = mundo();
		assertThat(permissionService.requireInformeVersion(m.auditor1, m.borradorTec1).getPublicId())
			.isEqualTo(m.borradorTec1);
		assertThat(permissionService.requireInformeVersion(m.cliente1, m.publicadaTec1).getPublicId())
			.isEqualTo(m.publicadaTec1);
		assertThat(permissionService.requireInformeArchivo(m.cliente1, m.archivoPublicado).getPublicId())
			.isEqualTo(m.archivoPublicado);

		assertThatThrownBy(() -> permissionService.requireInformeVersion(m.cliente1, m.borradorTec1))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> permissionService.requireInformeVersion(m.cliente1, m.despublicadaTec1))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> permissionService.requireInformeVersion(m.auditor2, m.publicadaTec1))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> permissionService.requireInformeVersion(m.cliente2, m.publicadaTec1))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> permissionService.requireInformeArchivo(m.cliente1, m.archivoDespublicado))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> permissionService.requireInformeVersion(m.auditor1, UUID.randomUUID().toString()))
			.isInstanceOf(ScopedNotFoundException.class);

		// Con el filtro de cuenta de Hibernate activo (como en una petición real), la cuenta B no ve nada de la A.
		assertThatThrownBy(() -> tenants.execute(m.cuentaB,
			() -> permissionService.requireInformeVersion(m.auditorB, m.publicadaTec1)))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> tenants.execute(m.cuentaB,
			() -> permissionService.requireInformeArchivo(m.clienteB, m.archivoPublicado)))
			.isInstanceOf(ScopedNotFoundException.class);
		// Y lo suyo sí lo ve.
		tenants.execute(m.cuentaB, () -> assertThat(
			permissionService.requireInformeVersion(m.auditorB, m.publicadaCuentaB).getPublicId())
			.isEqualTo(m.publicadaCuentaB));
	}

	@Test
	void conElFiltroDeCuentaActivoLasSeriesAjenasNoAparecen() {
		Mundo m = mundo();
		tenants.execute(m.cuentaB, () -> {
			assertThat(informes.findByProyectoIdAndCuentaIdOrderByTipoAsc(m.proyecto1, m.cuentaA))
				.as("filtro de cuenta + parámetro de otra cuenta").isEmpty();
			assertThat(informes.findByProyectoIdAndTipoAndCuentaId(m.proyecto1, TipoInforme.TEC, m.cuentaA)).isEmpty();
			assertThat(informes.findByPublicIdAndCuentaId(m.publicIdInformeTec1, m.cuentaA)).isEmpty();
		});
		tenants.execute(m.cuentaA, () -> {
			assertThat(informes.findByProyectoIdAndCuentaIdOrderByTipoAsc(m.proyecto1, m.cuentaA))
				.extracting(Informe::getTipo).containsExactlyInAnyOrder(TipoInforme.TEC, TipoInforme.EJE);
			assertThat(informes.findByProyectoIdAndTipoAndCuentaId(m.proyecto1, TipoInforme.TEC, m.cuentaA)).isPresent();
			assertThat(archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(
				versiones.findByPublicIdAndCuentaId(m.publicadaTec1, m.cuentaA).orElseThrow().getId(), m.cuentaA))
				.extracting(InformeArchivo::getFormato).containsExactly(FormatoInforme.PDF);
		});
	}

	// ---------------------------------------------------------------- utilidades

	private java.util.Optional<InformeVersion> visible(String publicId, TinyHatPrincipal p) {
		return versiones.findVisibleByPublicId(publicId, p.getCuentaId(), p.getOrganizacionId(), p.getUserId(), p.getRol());
	}

	private java.util.Optional<InformeArchivo> archivo(String publicId, TinyHatPrincipal p) {
		return archivos.findVisibleByPublicId(publicId, p.getCuentaId(), p.getOrganizacionId(), p.getUserId(), p.getRol());
	}

	/** Códigos de error de MySQL: 3819 restricción CHECK, 1062 valor duplicado, 1452 clave foránea. */
	private static final int CHECK = 3819;
	private static final int DUPLICADO = 1062;
	private static final int CLAVE_FORANEA = 1452;
	/** SIGNAL SQLSTATE '45000' sin código propio (V33). */
	private static final int DISPARADOR = 1644;

	/** La sentencia debe fallar por la restricción indicada (no por otra) y no dejar nada guardado. */
	private void rechaza(int codigoMySql, String sql, Object... argumentos) {
		Throwable error = org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(sql, argumentos));
		assertThat(error).as(sql).isNotNull();
		Throwable causa = error;
		while (causa.getCause() != null && !(causa instanceof java.sql.SQLException)) {
			causa = causa.getCause();
		}
		assertThat(causa).as("causa raíz de " + sql).isInstanceOf(java.sql.SQLException.class);
		assertThat(((java.sql.SQLException) causa).getErrorCode()).as(causa.getMessage()).isEqualTo(codigoMySql);
	}

	private long serie(Mundo m, long proyecto, long organizacion, TipoInforme tipo, TinyHatPrincipal autor) {
		AtomicReference<Long> id = new AtomicReference<>();
		tenants.execute(m.cuentaA, () -> id.set(informes.saveAndFlush(
			new Informe(organizacion, proyecto, tipo, (short) 2026, autor.getUserId())).getId()));
		return id.get();
	}

	/** Dos cuentas; la A con dos organizaciones y proyectos; la B con uno. Cada uno con sus usuarios e informes. */
	private Mundo mundo() {
		Mundo m = new Mundo();
		m.cuentaA = cuenta("Cuenta A");
		m.cuentaB = cuenta("Cuenta B");

		tenants.execute(m.cuentaA, () -> {
			Empresa org1 = empresa(m.cuentaA);
			Empresa org2 = empresa(m.cuentaA);
			m.org1 = org1.getId();
			m.org2 = org2.getId();
			m.proyecto1 = proyecto(m.cuentaA, org1).getIdAuditoria();
			m.proyecto2 = proyecto(m.cuentaA, org2).getIdAuditoria();
		});
		tenants.execute(m.cuentaB, () -> {
			Empresa orgB = empresa(m.cuentaB);
			m.orgB = orgB.getId();
			m.proyectoB = proyecto(m.cuentaB, orgB).getIdAuditoria();
		});

		m.auditor1 = usuario(m.cuentaA, null, "AUDITOR");
		m.auditor2 = usuario(m.cuentaA, null, "AUDITOR");
		m.cliente1 = usuario(m.cuentaA, m.org1, "CLIENTE_ADMIN");
		m.cliente2 = usuario(m.cuentaA, m.org2, "CLIENTE_ADMIN");
		m.auditorB = usuario(m.cuentaB, null, "AUDITOR");
		m.clienteB = usuario(m.cuentaB, m.orgB, "CLIENTE_ADMIN");
		jdbc.update("INSERT INTO asignacion_cliente_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)", m.cliente1.getUserId(), m.proyecto1, m.cuentaA);
		jdbc.update("INSERT INTO asignacion_cliente_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)", m.cliente2.getUserId(), m.proyecto2, m.cuentaA);
		jdbc.update("INSERT INTO asignacion_cliente_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)", m.clienteB.getUserId(), m.proyectoB, m.cuentaB);

		tenants.execute(m.cuentaA, () -> {
			asignar(m.auditor1, m.proyecto1, m.cuentaA);
			asignar(m.auditor2, m.proyecto2, m.cuentaA);

			Informe tec1 = informes.saveAndFlush(
				new Informe(m.org1, m.proyecto1, TipoInforme.TEC, (short) 2026, m.auditor1.getUserId()));
			m.publicIdInformeTec1 = tec1.getPublicId();
			InformeVersion v1 = versiones.saveAndFlush(
				new InformeVersion(tec1.getId(), 1, m.auditor1.getUserId(), NARRATIVA));
			v1.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(v1);
			archivoDe(v1, FormatoInforme.PDF);
			m.archivoDespublicado = archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(v1.getId(), m.cuentaA)
				.get(0).getPublicId();
			v1.despublicar("Se corrige un hallazgo del informe", LocalDateTime.now());
			versiones.saveAndFlush(v1);
			m.despublicadaTec1 = v1.getPublicId();

			InformeVersion v2 = versiones.saveAndFlush(
				new InformeVersion(tec1.getId(), 2, m.auditor1.getUserId(), NARRATIVA));
			v2.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(v2);
			m.publicadaTec1 = v2.getPublicId();
			m.versionPublicadaTec1 = v2.getId();
			archivoDe(v2, FormatoInforme.PDF);
			m.archivoPublicado = archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(v2.getId(), m.cuentaA)
				.get(0).getPublicId();

			m.borradorTec1 = versiones.saveAndFlush(
				new InformeVersion(tec1.getId(), 3, m.auditor1.getUserId(), NARRATIVA)).getPublicId();

			Informe eje1 = informes.saveAndFlush(
				new Informe(m.org1, m.proyecto1, TipoInforme.EJE, (short) 2026, m.auditor1.getUserId()));
			m.borradorEje1 = versiones.saveAndFlush(
				new InformeVersion(eje1.getId(), 1, m.auditor1.getUserId(), "{}")).getPublicId();

			Informe tec2 = informes.saveAndFlush(
				new Informe(m.org2, m.proyecto2, TipoInforme.TEC, (short) 2026, m.auditor2.getUserId()));
			InformeVersion p2 = versiones.saveAndFlush(
				new InformeVersion(tec2.getId(), 1, m.auditor2.getUserId(), NARRATIVA));
			p2.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(p2);
			m.publicadaProyecto2 = p2.getPublicId();
		});
		tenants.execute(m.cuentaB, () -> {
			asignar(m.auditorB, m.proyectoB, m.cuentaB);
			Informe informeB = informes.saveAndFlush(
				new Informe(m.orgB, m.proyectoB, TipoInforme.TEC, (short) 2026, m.auditorB.getUserId()));
			InformeVersion vb = versiones.saveAndFlush(
				new InformeVersion(informeB.getId(), 1, m.auditorB.getUserId(), NARRATIVA));
			vb.publicar("{\"schemaVersion\":1}", SHA, "2026.1", LocalDateTime.now());
			versiones.saveAndFlush(vb);
			m.publicadaCuentaB = vb.getPublicId();
		});
		return m;
	}

	private void archivoDe(InformeVersion version, FormatoInforme formato) {
		archivos.saveAndFlush(new InformeArchivo(
			version.getId(), formato, "org/" + UUID.randomUUID() + "." + formato.extension(), SHA, 1234));
	}

	private Empresa empresa(long cuenta) {
		Empresa organizacion = new Empresa();
		organizacion.setNombre("Org " + UUID.randomUUID());
		organizacion.setNit(UUID.randomUUID().toString());
		organizacion.setSector("Tecnología");
		organizacion.setCuentaId(cuenta);
		return empresaRepository.save(organizacion);
	}

	private Auditoria proyecto(long cuenta, Empresa organizacion) {
		Auditoria proyecto = new Auditoria();
		proyecto.setNombre("Proyecto " + UUID.randomUUID());
		proyecto.setTipoAuditoria("Auditoría de seguridad");
		proyecto.setFechaInico(LocalDate.now());
		proyecto.setFechaFin(LocalDate.now().plusDays(10));
		proyecto.setEstado("En Progreso");
		proyecto.setEmpresa(organizacion);
		proyecto.setCuentaId(cuenta);
		return auditoriaRepository.save(proyecto);
	}

	private void asignar(TinyHatPrincipal auditor, long proyecto, long cuenta) {
		asignaciones.save(new AsignacionAuditorProyecto(
			new AsignacionAuditorProyectoId(auditor.getUserId(), proyecto, cuenta), LocalDateTime.now()));
	}

	private TinyHatPrincipal usuario(long cuenta, Long organizacion, String rol) {
		String correo = rol.toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, password_hash, "
			+ "public_id) VALUES (?, ?, ?, 'ACTIVO', ?, 'test-hash', ?)",
			cuenta, organizacion, rol, correo, UUID.randomUUID().toString());
		Usuario usuario = usuarioRepository.findByEmailNormalizado(correo).orElseThrow();
		return new TinyHatPrincipal(usuario, true);
	}

	private long cuenta(String nombre) {
		String publicId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
			+ "VALUES (?, ?, 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))", publicId, nombre);
		return jdbc.queryForObject("SELECT id FROM cuenta WHERE public_id = ?", Long.class, publicId);
	}

	/** Todo lo que crea {@link #mundo()}: identificadores internos y públicos, y los usuarios de cada rol. */
	private static final class Mundo {
		long cuentaA;
		long cuentaB;
		long org1;
		long org2;
		long orgB;
		long proyecto1;
		long proyecto2;
		long proyectoB;
		long versionPublicadaTec1;
		String publicIdInformeTec1;
		TinyHatPrincipal auditor1;
		TinyHatPrincipal auditor2;
		TinyHatPrincipal cliente1;
		TinyHatPrincipal cliente2;
		TinyHatPrincipal auditorB;
		TinyHatPrincipal clienteB;
		String borradorTec1;
		String publicadaTec1;
		String despublicadaTec1;
		String borradorEje1;
		String publicadaProyecto2;
		String publicadaCuentaB;
		String archivoPublicado;
		String archivoDespublicado;
	}
}
