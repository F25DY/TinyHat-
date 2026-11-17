package com.hexora.hexora.informes.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.EstadoVersion;
import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Cve;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.Informe;
import com.hexora.hexora.model.InformeArchivo;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.CveRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * El ensamblador sobre MySQL real: lee solo lo del proyecto y de la cuenta, toma los CVE de la caché local y no
 * tiene ninguna dependencia que acceda a la red.
 */
@SpringBootTest(properties = {
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=3600000",
	"hexora.network.discovery-interval-ms=3600000",
	"hexora.network.retention-cleanup-interval-ms=3600000",
	"tinyhat.security.mfa.required=false",
	"tinyhat.informes.max-hallazgos=6"
})
class EnsambladorSnapshotIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final Instant AHORA = Instant.parse("2026-11-20T15:15:00Z");
	private static final String SHA_A = "a".repeat(64);
	private static final String SHA_B = "b".repeat(64);

	@Autowired private JdbcTemplate jdbc;
	@Autowired private TenantTaskExecutor tenants;
	@Autowired private EnsambladorSnapshot ensamblador;
	@Autowired private EmpresaRepository empresaRepository;
	@Autowired private AuditoriaRepository auditoriaRepository;
	@Autowired private AreaAuditableRepository areaRepository;
	@Autowired private VulnerabilidadRepository vulnerabilidadRepository;
	@Autowired private CveRepository cveRepository;
	@Autowired private InformeRepository informes;
	@Autowired private InformeVersionRepository versiones;
	@Autowired private InformeArchivoRepository archivos;

	// ---------------------------------------------------------------- contenido

	@Test
	void ensamblaElProyectoConSusAreasHallazgosCveYAnexoA() {
		Mundo m = mundo();
		InformeSnapshot s = ensamblar(m, TipoInforme.TEC, false);

		assertThat(s.informe().tipo()).isEqualTo("TEC");
		assertThat(s.informe().id()).matches("TH-2026-[0-9A-F]{4}-[0-9A-F]{4}-TEC-v1");
		assertThat(s.informe().id()).contains(m.org4).contains(m.pry4);
		assertThat(s.generadoEn()).isEqualTo("2026-11-20T10:15:00-05:00");
		assertThat(s.organizacion().nombre()).isEqualTo(m.nombreOrganizacion);
		assertThat(s.auditor().nombre()).as("nombre de la cuenta").isEqualTo("Cuenta Auditora Demo");
		assertThat(s.proyecto().nombre()).isEqualTo(m.nombreProyecto);
		assertThat(s.proyecto().fechaInicio()).isEqualTo(LocalDate.now().toString());

		assertThat(s.areas()).extracting(InformeSnapshot.Area::nombre)
			.containsExactly("Aplicaciones", "Red", "Zona vacía");
		assertThat(s.metricas().totalAreas()).isEqualTo(3);
		assertThat(s.metricas().totalHallazgos()).as("sin el aceptado").isEqualTo(4);
		assertThat(s.metricas().aceptados()).isEqualTo(1);
		assertThat(s.anexoA()).singleElement().satisfies(entrada -> {
			assertThat(entrada.titulo()).isEqualTo("Riesgo asumido");
			assertThat(entrada.area()).isEqualTo("Aplicaciones");
			assertThat(entrada.tipo()).isEqualTo("ACEPTADO");
		});

		Hallazgo conCve = hallazgo(s, "Log4Shell de prueba");
		assertThat(conCve.cves()).singleElement().satisfies(cve -> {
			assertThat(cve.id()).isEqualTo(m.cveEnCache);
			assertThat(cve.cvss()).isEqualByComparingTo("10.0");
			assertThat(cve.publicado()).isEqualTo("2021-12-10");
			assertThat(cve.url()).isEqualTo("https://nvd.nist.gov/vuln/detail/" + m.cveEnCache);
		});
		assertThat(conCve.referencias()).containsExactly("https://vendor.example/advisory");
		assertThat(conCve.codigo()).matches("H-[0-9A-F]{6}");
		assertThat(conCve.mitigacion()).isEqualTo("Actualizar a la versión corregida");

		Hallazgo sinCache = hallazgo(s, "CVE sin caché");
		assertThat(sinCache.cves()).singleElement().satisfies(cve -> {
			assertThat(cve.id()).isEqualTo("CVE-2096-99999");
			assertThat(cve.cvss()).isNull();
			assertThat(cve.publicado()).isNull();
		});
		assertThat(hallazgo(s, "Texto libre en el campo CVE").cves()).isEmpty();
		assertThat(hallazgo(s, "Sin plan de mitigación").mitigacion()).isNull();

		assertThat(s.referenciasApa()).anyMatch(r -> r.contains(m.cveEnCache + " detail*") && r.contains("(2021)"))
			.anyMatch(r -> r.contains("CVE-2096-99999 detail*") && r.contains("(s.f.)"));
	}

	@Test
	void noEntraNadaDeOtroProyectoDeLaMismaCuentaNiDeOtraCuenta() {
		Mundo m = mundo();
		InformeSnapshot s = ensamblar(m, TipoInforme.TEC, false);
		String todo = SerializadorSnapshot.serializar(s);
		assertThat(todo).doesNotContain("Hallazgo del otro proyecto").doesNotContain("Hallazgo de otra cuenta")
			.doesNotContain("Área del otro proyecto").doesNotContain("Área de otra cuenta")
			.doesNotContain(m.nombreOtraOrganizacion).doesNotContain("Cuenta Ajena");
		assertThat(s.areas().stream().flatMap(a -> a.hallazgos().stream()).map(Hallazgo::titulo))
			.doesNotContain("Hallazgo del otro proyecto", "Hallazgo de otra cuenta");
	}

	@Test
	void elMismoProyectoDaElMismoSnapshotYElMismoHash() {
		Mundo m = mundo();
		String a = SerializadorSnapshot.huella(ensamblar(m, TipoInforme.EJE, false));
		String b = SerializadorSnapshot.huella(ensamblar(m, TipoInforme.EJE, false));
		assertThat(a).isEqualTo(b).matches("[0-9a-f]{64}");
		assertThat(SerializadorSnapshot.huella(ensamblar(m, TipoInforme.EJE, true)))
			.as("la copia NP lleva otro ID y por tanto otro hash").isNotEqualTo(a);
	}

	@Test
	void unaCopiaDeTrabajoLlevaSufijoNpYEstadoBorrador() {
		Mundo m = mundo();
		InformeSnapshot s = tenantsEnsamblar(m, TipoInforme.MIT, 3, EstadoVersion.BORRADOR, true);
		assertThat(s.informe().id()).endsWith("-MIT-v3-NP");
		assertThat(s.informe().estado()).isEqualTo("BORRADOR");
		assertThat(s.informe().version()).isEqualTo(3);
	}

	// ---------------------------------------------------------------- trazabilidad

	@Test
	void elInformeFinalListaLasVersionesPublicadasVigentesDeLosOtrosTiposConSusHashes() {
		Mundo m = mundo();
		InformeSnapshot fin = ensamblar(m, TipoInforme.FIN, false);
		assertThat(fin.trazabilidad()).hasSize(2);
		assertThat(fin.trazabilidad()).extracting(InformeSnapshot.EntradaTrazabilidad::tipo).containsOnly("TEC");
		assertThat(fin.trazabilidad()).extracting(InformeSnapshot.EntradaTrazabilidad::formato)
			.containsExactly("DOCX", "PDF");
		assertThat(fin.trazabilidad()).extracting(InformeSnapshot.EntradaTrazabilidad::sha256)
			.containsExactly(SHA_B, SHA_A);
		assertThat(fin.trazabilidad()).extracting(InformeSnapshot.EntradaTrazabilidad::id)
			.containsOnly("TH-2026-" + m.org4 + "-" + m.pry4 + "-TEC-v1");
		assertThat(fin.trazabilidad().get(0).publicadoEn()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-05:00");
		// No aparecen los borradores, los despublicados ni el propio informe final.
		assertThat(fin.trazabilidad()).extracting(InformeSnapshot.EntradaTrazabilidad::tipo)
			.doesNotContain("EJE", "MIT", "FIN");
	}

	@Test
	void losDemasTiposNoLlevanTrazabilidad() {
		Mundo m = mundo();
		assertThat(ensamblar(m, TipoInforme.TEC, false).trazabilidad()).isEmpty();
		assertThat(ensamblar(m, TipoInforme.MR, false).trazabilidad()).isEmpty();
	}

	// ---------------------------------------------------------------- límite y red

	@Test
	void masDeMaxHallazgosEsUnErrorDeValidacion() {
		Mundo m = mundo();
		AtomicReference<Long> proyecto = new AtomicReference<>();
		tenants.execute(m.cuenta, () -> {
			Auditoria grande = proyecto(m.cuenta, empresa(m.cuenta));
			AreaAuditable area = area(grande, "Única");
			for (int n = 0; n < 7; n++) {
				vulnerabilidad(area, "Vuln " + n, 5, 5, 5, 5, "Abierta", "Plan", null);
			}
			proyecto.set(grande.getIdAuditoria());
		});
		assertThatThrownBy(() -> tenants.execute(m.cuenta, () -> ensamblador.ensamblar(
			auditoriaRepository.findById(proyecto.get()).orElseThrow(), TipoInforme.TEC, 1, EstadoVersion.PUBLICADO,
			false, 2026, Map.of(), AHORA)))
			.isInstanceOf(InformeValidacionException.class)
			.hasMessageContaining("7 vulnerabilidades").hasMessageContaining("6");
	}

	@Test
	void elEnsambladorNoTieneNingunaDependenciaQueAccedaALaRed() throws Exception {
		List<Class<?>> tipos = new ArrayList<>();
		for (Constructor<?> constructor : EnsambladorSnapshot.class.getDeclaredConstructors()) {
			tipos.addAll(List.of(constructor.getParameterTypes()));
		}
		for (Field campo : EnsambladorSnapshot.class.getDeclaredFields()) {
			tipos.add(campo.getType());
		}
		for (Class<?> tipo : tipos) {
			String nombre = tipo.getName();
			assertThat(nombre).as("dependencia " + nombre)
				.doesNotContain("CveService").doesNotContain("RestClient").doesNotContain("RestTemplate")
				.doesNotContain("WebClient").doesNotContain("HttpClient").doesNotContain("TraductorService")
				.doesNotContain("SecureRestClientFactory").doesNotContain("NvdRateLimitService");
		}
		// Lo mismo para el constructor puro del snapshot.
		for (Class<?> clase : List.of(ConstructorSnapshot.class, ReferenciasApa.class)) {
			for (Field campo : clase.getDeclaredFields()) {
				assertThat(campo.getType().getName()).doesNotContain("RestClient").doesNotContain("Http");
			}
		}
	}

	// ---------------------------------------------------------------- utilidades

	private InformeSnapshot ensamblar(Mundo m, TipoInforme tipo, boolean noPublicado) {
		return tenantsEnsamblar(m, tipo, 1, noPublicado ? EstadoVersion.BORRADOR : EstadoVersion.PUBLICADO, noPublicado);
	}

	private InformeSnapshot tenantsEnsamblar(
		Mundo m, TipoInforme tipo, int version, EstadoVersion estado, boolean noPublicado
	) {
		AtomicReference<InformeSnapshot> resultado = new AtomicReference<>();
		tenants.execute(m.cuenta, () -> resultado.set(ensamblador.ensamblar(
			auditoriaRepository.findById(m.proyecto).orElseThrow(), tipo, version, estado, noPublicado, 2026,
			Map.of("resumenEjecutivo", "Resumen ficticio"), AHORA)));
		return resultado.get();
	}

	private static Hallazgo hallazgo(InformeSnapshot s, String titulo) {
		return s.areas().stream().flatMap(a -> a.hallazgos().stream())
			.filter(h -> h.titulo().equals(titulo)).findFirst().orElseThrow();
	}

	private Mundo mundo() {
		Mundo m = new Mundo();
		m.cuenta = cuenta("Cuenta Auditora Demo");
		long ajena = cuenta("Cuenta Ajena");
		m.cveEnCache = "CVE-2097-" + String.format("%05d", (int) (Math.random() * 90000) + 10000);
		m.nombreOtraOrganizacion = "Otra organización " + UUID.randomUUID();

		long usuario = usuario(m.cuenta);
		tenants.execute(m.cuenta, () -> {
			Empresa org = empresa(m.cuenta);
			m.nombreOrganizacion = org.getNombre();
			Auditoria proyecto = proyecto(m.cuenta, org);
			m.proyecto = proyecto.getIdAuditoria();
			m.nombreProyecto = proyecto.getNombre();
			m.org4 = org.getPublicId().replace("-", "").substring(0, 4).toUpperCase();
			m.pry4 = proyecto.getPublicId().replace("-", "").substring(0, 4).toUpperCase();

			Cve cve = new Cve();
			cve.setCodigoCve(m.cveEnCache);
			cve.setDescripcion("Descripción ficticia");
			cve.setPuntajeCvss(new BigDecimal("10.0"));
			cve.setSeveridad("CRITICAL");
			cve.setFechaPublicacion(LocalDateTime.of(2021, 12, 10, 10, 15));
			cve.setReferenciasParche("https://vendor.example/advisory\njavascript:alert(1)\nftp://x");
			cveRepository.save(cve);

			AreaAuditable red = area(proyecto, "Red");
			AreaAuditable apps = area(proyecto, "Aplicaciones");
			area(proyecto, "Zona vacía");
			vulnerabilidad(red, "Log4Shell de prueba", 9, 2, 1, 8, "Abierta", "Actualizar a la versión corregida", m.cveEnCache);
			vulnerabilidad(red, "CVE sin caché", 5, 5, 5, 5, "En Progreso", "Plan", "CVE-2096-99999");
			vulnerabilidad(apps, "Texto libre en el campo CVE", 10, 10, 10, 10, "Abierta", "Plan", "algo cualquiera");
			vulnerabilidad(apps, "Riesgo asumido", 8, 8, 8, 9, "Aceptada", "Plan", null);
			vulnerabilidad(apps, "Sin plan de mitigación", 1, 0, 0, 1, "Mitigada", null, null);

			// Otro proyecto de la misma cuenta, de otra organización: no debe entrar.
			Empresa otraOrg = empresa(m.cuenta);
			otraOrg.setNombre(m.nombreOtraOrganizacion);
			empresaRepository.save(otraOrg);
			Auditoria otro = proyecto(m.cuenta, otraOrg);
			vulnerabilidad(area(otro, "Área del otro proyecto"), "Hallazgo del otro proyecto", 9, 9, 9, 9, "Abierta",
				"Plan", null);

			// Informes publicados del proyecto, para la trazabilidad del final.
			serie(m, proyecto, org, TipoInforme.TEC, usuario, EstadoVersion.PUBLICADO, FormatoInforme.PDF,
				FormatoInforme.DOCX);
			serie(m, proyecto, org, TipoInforme.EJE, usuario, EstadoVersion.BORRADOR);
			serie(m, proyecto, org, TipoInforme.MIT, usuario, EstadoVersion.DESPUBLICADO, FormatoInforme.PDF);
		});
		tenants.execute(ajena, () -> {
			Auditoria ajeno = proyecto(ajena, empresa(ajena));
			vulnerabilidad(area(ajeno, "Área de otra cuenta"), "Hallazgo de otra cuenta", 9, 9, 9, 9, "Abierta",
				"Plan", null);
		});
		return m;
	}

	private void serie(
		Mundo m, Auditoria proyecto, Empresa org, TipoInforme tipo, long usuario, EstadoVersion estado,
		FormatoInforme... formatos
	) {
		Informe informe = informes.saveAndFlush(
			new Informe(org.getId(), proyecto.getIdAuditoria(), tipo, (short) 2026, usuario));
		InformeVersion version = versiones.saveAndFlush(new InformeVersion(informe.getId(), 1, usuario, "{}"));
		if (estado == EstadoVersion.BORRADOR) {
			return;
		}
		version.publicar("{\"schemaVersion\":1}", SHA_A, "2026.1", LocalDateTime.of(2026, 11, 18, 14, 0));
		versiones.saveAndFlush(version);
		for (FormatoInforme formato : formatos) {
			archivos.saveAndFlush(new InformeArchivo(version.getId(), formato,
				"ruta/" + UUID.randomUUID() + "." + formato.extension(),
				formato == FormatoInforme.PDF ? SHA_A : SHA_B, 100));
		}
		if (estado == EstadoVersion.DESPUBLICADO) {
			version.despublicar("Se corrige el informe publicado", LocalDateTime.of(2026, 11, 19, 9, 0));
			versiones.saveAndFlush(version);
		}
	}

	private Empresa empresa(long cuenta) {
		Empresa organizacion = new Empresa();
		organizacion.setNombre("Organización " + UUID.randomUUID());
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

	private AreaAuditable area(Auditoria proyecto, String nombre) {
		AreaAuditable area = new AreaAuditable();
		area.setNombre(nombre);
		area.setDescripcion("Descripción de " + nombre);
		area.setAuditoria(proyecto);
		area.setCuentaId(proyecto.getCuentaId());
		return areaRepository.save(area);
	}

	private void vulnerabilidad(
		AreaAuditable area, String nombre, int c, int i, int a, int probabilidad, String estado, String plan, String cve
	) {
		Vulnerabilidad v = new Vulnerabilidad();
		v.setNombre(nombre);
		v.setTipo("Configuración");
		v.setConfidencialidad(c);
		v.setIntegridad(i);
		v.setDisponibilidad(a);
		v.setProbabilidad(probabilidad);
		v.setImpacto(new BigDecimal("5.00"));
		v.setNivelRiesgo("Medio");
		v.setEstado(estado);
		v.setPlanMitigacion(plan);
		v.setCve(cve);
		v.setFechaDeteccion(LocalDate.now());
		v.setAreaAuditable(area);
		v.setCuentaId(area.getCuentaId());
		vulnerabilidadRepository.save(v);
	}

	private long cuenta(String nombre) {
		String publicId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
			+ "VALUES (?, ?, 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))", publicId, nombre);
		return jdbc.queryForObject("SELECT id FROM cuenta WHERE public_id = ?", Long.class, publicId);
	}

	private long usuario(long cuenta) {
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, password_hash, "
			+ "public_id) VALUES (?, NULL, 'AUDITOR', 'ACTIVO', ?, 'test-hash', ?)",
			cuenta, "ensamblador-" + UUID.randomUUID() + "@example.test", UUID.randomUUID().toString());
		return jdbc.queryForObject("SELECT MAX(id) FROM usuario WHERE cuenta_id = ?", Long.class, cuenta);
	}

	private static final class Mundo {
		long cuenta;
		long proyecto;
		String nombreOrganizacion;
		String nombreProyecto;
		String nombreOtraOrganizacion;
		String org4;
		String pry4;
		String cveEnCache;
	}
}
