package com.hexora.hexora.informes.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.hexora.hexora.informes.EstadoVersion;
import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.render.docx.RenderizadorDocx;
import com.hexora.hexora.informes.render.pdf.RenderizadorPdf;
import com.hexora.hexora.informes.servicio.InformeService.Descarga;
import com.hexora.hexora.informes.servicio.InformeService.ResultadoPublicacion;
import com.hexora.hexora.informes.servicio.InformeService.Verificacion;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.SerializadorSnapshot;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Empresa;
import com.hexora.hexora.model.InformeArchivo;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.repository.EmpresaRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import com.hexora.hexora.security.RateLimitExceededException;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.test.IsolatedMySqlTestDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Ciclo de vida de los informes sobre MySQL real y con archivos en disco: ciclo completo, invariantes I1 a I6,
 * publicación fallida sin rastros, integridad, concurrencia, rate limit, aislamiento y auditoría sin contenido.
 */
@SpringBootTest(properties = {
	"hexora.network.discovery-enabled=false",
	"hexora.network.monitor-interval-ms=3600000",
	"hexora.network.discovery-interval-ms=3600000",
	"hexora.network.retention-cleanup-interval-ms=3600000",
	"tinyhat.security.mfa.required=false",
	"tinyhat.informes.concurrencia-max=1",
	"tinyhat.informes.espera-concurrencia-ms=300"
})
class InformeServiceIntegrationTest extends IsolatedMySqlTestDatabase {

	private static final Path DIRECTORIO = crearDirectorio();
	private static final String MARCA = "SECRETO-NARRATIVA-XYZ";
	private static final String TITULO_HALLAZGO = "Inyección SQL confidencial del cliente";

	private static Path crearDirectorio() {
		try {
			return Files.createTempDirectory("informes-prueba-");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void directorioDeInformes(DynamicPropertyRegistry registro) {
		registro.add("tinyhat.informes.dir", () -> DIRECTORIO.toString());
	}

	@Autowired private JdbcTemplate jdbc;
	@Autowired private TenantTaskExecutor tenants;
	@Autowired private InformeService servicio;
	@Autowired private EmpresaRepository empresas;
	@Autowired private AuditoriaRepository proyectos;
	@Autowired private AreaAuditableRepository areas;
	@Autowired private VulnerabilidadRepository vulnerabilidades;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private InformeVersionRepository versiones;
	@Autowired private InformeArchivoRepository archivos;

	@MockitoSpyBean private RenderizadorDocx docx;
	@MockitoSpyBean private RenderizadorPdf pdf;
	@MockitoSpyBean private AlmacenInformes almacen;
	@MockitoSpyBean private LogNotificadorInformes notificador;

	// ================================================================ ciclo completo

	@Test
	void cicloCompletoBorradorNarrativaGenerarPublicarDescargarVerificarDespublicarYNuevaVersion() throws Exception {
		Mundo m = mundo();
		InformeVersion borrador = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.TEC));
		assertThat(borrador.getEstado()).isEqualTo(EstadoVersion.BORRADOR);
		assertThat(borrador.getNumero()).isEqualTo(1);

		// Sin la sección obligatoria no se publica, y no queda nada.
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, borrador.getPublicId(),
			EnumSet.of(FormatoInforme.PDF)))).isInstanceOf(InformeValidacionException.class)
			.hasMessageContaining("Alcance y metodología");

		como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, borrador.getPublicId(), "alcanceMetodologia",
			"Alcance **ficticio** de la prueba. " + MARCA));

		// Generar (NP) cada combinación válida del tipo, y la vista previa.
		for (FormatoInforme formato : TipoInforme.TEC.formatos()) {
			ByteArrayOutputStream salida = new ByteArrayOutputStream();
			como(m.auditor, () -> {
				servicio.generarNoPublicado(m.auditor, borrador.getPublicId(), formato, salida);
				return null;
			});
			assertThat(salida.size()).as("copia NP " + formato).isPositive();
			assertThat(new String(salida.toByteArray(), 0, 2, StandardCharsets.ISO_8859_1))
				.isEqualTo(formato == FormatoInforme.PDF ? "%P" : "PK");
		}
		ByteArrayOutputStream vista = new ByteArrayOutputStream();
		como(m.auditor, () -> {
			servicio.previsualizar(m.auditor, borrador.getPublicId(), vista);
			return null;
		});
		assertThat(new String(vista.toByteArray(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
		assertThat(archivosEnDisco(m)).as("generar no guarda nada en disco").isEmpty();
		assertThat(contar("informe_archivo", m.cuenta)).isZero();

		// Publicar PDF y DOCX.
		ResultadoPublicacion resultado = como(m.auditor, () -> servicio.publicar(m.auditor, borrador.getPublicId(),
			EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX)));
		assertThat(resultado.archivos()).extracting(InformeService.ArchivoPublicado::formato).containsExactly("PDF", "DOCX");
		InformeVersion publicada = versionDeBase(m.cuenta, borrador.getPublicId());
		assertThat(publicada.getEstado()).isEqualTo(EstadoVersion.PUBLICADO);
		assertThat(publicada.getSnapshotSha256()).isEqualTo(resultado.snapshotSha256()).hasSize(64);
		// MySQL normaliza la columna JSON (orden y espacios): el hash se comprueba re-serializando de forma canónica.
		assertThat(SerializadorSnapshot.huella(SerializadorSnapshot.leer(publicada.getSnapshotJson()))).isEqualTo(publicada.getSnapshotSha256());
		assertThat(publicada.getPlantillaVersion()).isEqualTo("2026.2");
		assertThat(publicada.getPublicadoEn()).isNotNull();
		List<InformeArchivo> guardados = archivosDeBase(m.cuenta, publicada.getId());
		assertThat(guardados).hasSize(2);
		for (InformeArchivo archivo : guardados) {
			Path enDisco = DIRECTORIO.resolve(archivo.getRutaRelativa());
			assertThat(enDisco).exists();
			assertThat(archivo.getRutaRelativa()).doesNotContain("..").matches("[0-9a-f-]{36}/[0-9a-f-]{36}/v1/[0-9a-f-]{36}\\.(pdf|docx)");
			assertThat(AlmacenInformes.sha256(Files.readAllBytes(enDisco))).isEqualTo(archivo.getSha256());
			assertThat(Files.size(enDisco)).isEqualTo(archivo.getBytes());
		}
		assertThat(archivosEnDisco(m)).hasSize(2);
		assertThat(archivosTemporales()).as("sin temporales").isEmpty();

		// El CLIENTE_ADMIN de la organización descarga (hash verificado) y verifica.
		InformeArchivo pdfPublicado = guardados.stream().filter(a -> a.getFormato() == FormatoInforme.PDF).findFirst().orElseThrow();
		Descarga descarga = como(m.cliente, () -> servicio.descargar(m.cliente, pdfPublicado.getPublicId()));
		assertThat(descarga.mime()).isEqualTo("application/pdf");
		assertThat(descarga.nombre()).matches("TH-\\d{4}-[0-9A-F]{4}-[0-9A-F]{4}-TEC-v1\\.pdf");
		assertThat(AlmacenInformes.sha256(descarga.contenido())).isEqualTo(pdfPublicado.getSha256());
		Verificacion verificacion = como(m.cliente, () -> servicio.verificar(m.cliente, borrador.getPublicId()));
		assertThat(verificacion.snapshotSha256()).isEqualTo(publicada.getSnapshotSha256());
		assertThat(verificacion.archivos()).hasSize(2);
		assertThat(verificacion.estado()).isEqualTo("PUBLICADO");

		// Despublicar: el motivo es obligatorio; luego el cliente ya no lo ve (404) y el auditor sí.
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.despublicar(m.auditor, borrador.getPublicId(), "corto")))
			.isInstanceOf(IllegalArgumentException.class);
		como(m.auditor, () -> servicio.despublicar(m.auditor, borrador.getPublicId(), "Se corrige la clasificación de un hallazgo"));
		assertThat(versionDeBase(m.cuenta, borrador.getPublicId()).getEstado()).isEqualTo(EstadoVersion.DESPUBLICADO);
		assertThatThrownBy(() -> como(m.cliente, () -> servicio.verificar(m.cliente, borrador.getPublicId())))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> como(m.cliente, () -> servicio.descargar(m.cliente, pdfPublicado.getPublicId())))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThat(como(m.auditor, () -> servicio.descargar(m.auditor, pdfPublicado.getPublicId())).contenido()).isNotEmpty();
		assertThat(archivosEnDisco(m)).as("lo despublicado se conserva como evidencia").hasSize(2);

		// I6: la nueva versión precarga la narrativa de la anterior.
		InformeVersion v2 = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.TEC));
		assertThat(v2.getNumero()).isEqualTo(2);
		assertThat(versionDeBase(m.cuenta, v2.getPublicId()).getNarrativaJson()).contains(MARCA);

		// Auditoría: toda la secuencia, sin contenido de la narrativa ni de los hallazgos.
		List<String> eventos = eventosDe(borrador.getPublicId());
		assertThat(eventos).contains("INFORME_BORRADOR_CREADO", "INFORME_NARRATIVA_ACTUALIZADA", "INFORME_GENERADO",
			"INFORME_PREVISUALIZADO", "INFORME_PUBLICACION_FALLIDA", "INFORME_PUBLICADO", "INFORME_VERIFICADO",
			"INFORME_DESCARGADO", "INFORME_DESPUBLICADO");
		String todoElDetalle = String.join("\n", jdbc.queryForList(
			"SELECT CAST(detalle AS CHAR) FROM audit_event WHERE cuenta_id = ? AND evento LIKE 'INFORME\\_%'",
			String.class, m.cuenta));
		assertThat(todoElDetalle).doesNotContain(MARCA).doesNotContain(TITULO_HALLAZGO).doesNotContain("Alcance");
	}

	// ================================================================ invariantes

	@Test
	void i1SoloUnBorradorPorSerie() {
		Mundo m = mundo();
		como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.EJE));
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.EJE)))
			.isInstanceOf(ErroresInforme.Conflicto.class).hasMessageContaining("borrador");
		// Otro tipo es otra serie.
		assertThat(como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.MIT)).getNumero()).isEqualTo(1);
	}

	@Test
	void i2SoloUnaPublicadaPorSerieYElHistorialLaVeDespublicada() {
		Mundo m = mundo();
		InformeVersion v1 = publicarMit(m);
		InformeVersion v2 = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.MIT));
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, v2.getPublicId(), EnumSet.of(FormatoInforme.PDF))))
			.isInstanceOf(ErroresInforme.Conflicto.class).hasMessageContaining("Despublique la versión vigente");
		como(m.auditor, () -> servicio.despublicar(m.auditor, v1.getPublicId(), "Motivo suficiente de despublicación"));
		como(m.auditor, () -> servicio.publicar(m.auditor, v2.getPublicId(), EnumSet.of(FormatoInforme.PDF)));
		InformeVersion publicada = versionDeBase(m.cuenta, v2.getPublicId());
		assertThat(publicada.getEstado()).isEqualTo(EstadoVersion.PUBLICADO);
		InformeSnapshot snapshot = SerializadorSnapshot.leer(publicada.getSnapshotJson());
		assertThat(snapshot.historial()).extracting(InformeSnapshot.EntradaHistorial::estado)
			.containsExactly("DESPUBLICADO", "PUBLICADO");
		assertThat(snapshot.informe().id()).endsWith("-MIT-v2").doesNotContain("-NP");
	}

	@Test
	void i3I5LoPublicadoNoSeEditaNiSeBorraYLasTransicionesSonLasValidas() {
		Mundo m = mundo();
		InformeVersion publicada = publicarMit(m);
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, publicada.getPublicId(), "introduccion", "x")))
			.isInstanceOf(ErroresInforme.Conflicto.class);
		assertThatThrownBy(() -> como(m.auditor, () -> {
			servicio.eliminarBorrador(m.auditor, publicada.getPublicId());
			return null;
		})).isInstanceOf(ErroresInforme.Conflicto.class);
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, publicada.getPublicId(), EnumSet.of(FormatoInforme.PDF))))
			.isInstanceOf(ErroresInforme.Conflicto.class);
		InformeVersion borrador = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.EJE));
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.despublicar(m.auditor, borrador.getPublicId(), "Motivo suficiente de despublicación")))
			.isInstanceOf(ErroresInforme.Conflicto.class);
		// Un borrador sí se elimina físicamente.
		como(m.auditor, () -> {
			servicio.eliminarBorrador(m.auditor, borrador.getPublicId());
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe_version WHERE public_id = ?", Integer.class, borrador.getPublicId())).isZero();
		assertThat(eventosDe(borrador.getPublicId())).contains("INFORME_BORRADOR_ELIMINADO");
		// La versión publicada sigue intacta.
		assertThat(versionDeBase(m.cuenta, publicada.getPublicId()).getEstado()).isEqualTo(EstadoVersion.PUBLICADO);
	}

	@Test
	void i4SoloHayArchivosDeVersionesPublicadasODespublicadas() {
		Mundo m = mundo();
		InformeVersion borrador = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.MIT));
		como(m.auditor, () -> {
			servicio.generarNoPublicado(m.auditor, borrador.getPublicId(), FormatoInforme.XLSX, new ByteArrayOutputStream());
			return null;
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM informe_archivo a JOIN informe_version v ON v.id = a.version_id "
			+ "WHERE v.estado = 'BORRADOR' AND a.cuenta_id = ?", Integer.class, m.cuenta)).isZero();
	}

	@Test
	void formatosInvalidosSeRechazan() {
		Mundo m = mundo();
		InformeVersion mr = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.MR));
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, mr.getPublicId(), EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> como(m.auditor, () -> {
			servicio.generarNoPublicado(m.auditor, mr.getPublicId(), FormatoInforme.DOCX, new ByteArrayOutputStream());
			return null;
		})).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, mr.getPublicId(), Set.of())))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, mr.getPublicId(), EnumSet.of(FormatoInforme.XLSX))))
			.isInstanceOf(ErroresInforme.Conflicto.class).hasMessageContaining("PDF");
	}

	@Test
	void laNarrativaValidaLaSeccionYLaLongitud() {
		Mundo m = mundo();
		InformeVersion b = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.EJE));
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, b.getPublicId(), "noExiste", "x")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, b.getPublicId(), "resumenEjecutivo", "a".repeat(20001))))
			.isInstanceOf(InformeValidacionException.class);
		como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, b.getPublicId(), "resumenEjecutivo", "Texto"));
		como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, b.getPublicId(), "resumenEjecutivo", "  "));
		assertThat(versionDeBase(m.cuenta, b.getPublicId()).getNarrativaJson()).doesNotContain("resumenEjecutivo");
	}

	// ================================================================ publicación fallida

	@Test
	void unaPublicacionFallidaNoDejaPublicadoNiArchivosEnDisco() throws Exception {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		doThrow(new IllegalStateException("explotó el renderizador")).when(docx).render(any(), any(), any());
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(),
			EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX)))).isInstanceOf(ErroresInforme.Publicacion.class)
			.hasMessageNotContaining("explotó");
		assertThat(versionDeBase(m.cuenta, b.getPublicId()).getEstado()).isEqualTo(EstadoVersion.BORRADOR);
		assertThat(versionDeBase(m.cuenta, b.getPublicId()).getSnapshotJson()).isNull();
		assertThat(contar("informe_archivo", m.cuenta)).isZero();
		assertThat(archivosEnDisco(m)).isEmpty();
		assertThat(archivosTemporales()).isEmpty();
		assertThat(eventosDe(b.getPublicId())).contains("INFORME_PUBLICACION_FALLIDA");
		verify(notificador, after(400).never()).informePublicado(any());
		// El borrador quedó intacto: se puede publicar cuando el problema se corrige.
	}

	@Test
	void siFallaDespuesDeMoverElPrimerArchivoSeBorraLoMovido() throws Exception {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		doCallRealMethod().doThrow(new IOException("disco lleno")).when(almacen).mover(any(), any());
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(),
			EnumSet.of(FormatoInforme.PDF, FormatoInforme.DOCX)))).isInstanceOf(ErroresInforme.Publicacion.class);
		assertThat(archivosEnDisco(m)).as("el PDF ya movido se borró").isEmpty();
		assertThat(archivosTemporales()).isEmpty();
		assertThat(contar("informe_archivo", m.cuenta)).isZero();
		assertThat(versionDeBase(m.cuenta, b.getPublicId()).getEstado()).isEqualTo(EstadoVersion.BORRADOR);
	}

	@Test
	void unaFallaDeLaNotificacionNoAfectaLaPublicacion() {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		doThrow(new IllegalStateException("sin SMTP")).when(notificador).informePublicado(any());
		como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(), EnumSet.of(FormatoInforme.PDF)));
		assertThat(versionDeBase(m.cuenta, b.getPublicId()).getEstado()).isEqualTo(EstadoVersion.PUBLICADO);
		verify(notificador, timeout(5000)).informePublicado(any());
	}

	@Test
	void alPublicarSeNotificaDespuesDeConfirmarSoloConIdentificadores() {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(), EnumSet.of(FormatoInforme.PDF)));
		verify(notificador, timeout(5000)).informePublicado(argThat(evento ->
			evento.organizacionId().equals(m.organizacionId) && evento.cuentaId().equals(m.cuenta)
				&& evento.versionPublicId().equals(b.getPublicId())));
	}

	// ================================================================ integridad

	@Test
	void unArchivoAlteradoOFaltanteNoSeSirveYDejaEvento() throws Exception {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(), EnumSet.of(FormatoInforme.PDF)));
		InformeVersion v = versionDeBase(m.cuenta, b.getPublicId());
		InformeArchivo archivo = archivosDeBase(m.cuenta, v.getId()).get(0);
		Path enDisco = DIRECTORIO.resolve(archivo.getRutaRelativa());

		Files.write(enDisco, new byte[] {1, 2, 3}, java.nio.file.StandardOpenOption.APPEND);
		assertThatThrownBy(() -> como(m.cliente, () -> servicio.descargar(m.cliente, archivo.getPublicId())))
			.isInstanceOf(ErroresInforme.Integridad.class).hasMessageNotContaining(archivo.getRutaRelativa())
			.hasMessageNotContaining(archivo.getSha256());
		assertThat(eventosDe(b.getPublicId())).contains("INFORME_INTEGRIDAD_FALLIDA");

		Files.delete(enDisco);
		assertThatThrownBy(() -> como(m.auditor, () -> servicio.descargar(m.auditor, archivo.getPublicId())))
			.isInstanceOf(ErroresInforme.Integridad.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE evento = 'INFORME_INTEGRIDAD_FALLIDA' AND recurso_public_id = ?",
			Integer.class, b.getPublicId())).isEqualTo(2);
	}

	// ================================================================ concurrencia y rate limit

	@Test
	void conElMaximoOcupadoLaSiguienteGeneracionRecibe503() throws Exception {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		CountDownLatch dentro = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		doAnswer(llamada -> {
			dentro.countDown();
			soltar.await(15, TimeUnit.SECONDS);
			return llamada.callRealMethod();
		}).when(pdf).render(any(), any(), any());
		ExecutorService hilos = Executors.newSingleThreadExecutor();
		try {
			Future<?> primera = hilos.submit(() -> como(m.auditor, () -> {
				servicio.previsualizar(m.auditor, b.getPublicId(), new ByteArrayOutputStream());
				return null;
			}));
			assertThat(dentro.await(15, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> como(m.auditor, () -> {
				servicio.generarNoPublicado(m.auditor, b.getPublicId(), FormatoInforme.DOCX, new ByteArrayOutputStream());
				return null;
			})).isInstanceOf(ErroresInforme.Ocupado.class);
			assertThatThrownBy(() -> como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(), EnumSet.of(FormatoInforme.PDF))))
				.isInstanceOf(ErroresInforme.Ocupado.class);
			soltar.countDown();
			primera.get(30, TimeUnit.SECONDS);
		} finally {
			soltar.countDown();
			hilos.shutdownNow();
		}
		// Liberado el permiso, vuelve a funcionar.
		doCallRealMethod().when(pdf).render(any(), any(), any());
		como(m.auditor, () -> {
			servicio.generarNoPublicado(m.auditor, b.getPublicId(), FormatoInforme.DOCX, new ByteArrayOutputStream());
			return null;
		});
	}

	@Test
	void elRateLimitDeGeneracionEsPorUsuarioYQuedaAuditado() {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		TinyHatPrincipal otro = auditor(m, "otro-auditor");
		for (int n = 0; n < 5; n++) {
			como(m.auditor, () -> {
				servicio.generarNoPublicado(m.auditor, b.getPublicId(), FormatoInforme.DOCX, new ByteArrayOutputStream());
				return null;
			});
		}
		assertThatThrownBy(() -> como(m.auditor, () -> {
			servicio.generarNoPublicado(m.auditor, b.getPublicId(), FormatoInforme.DOCX, new ByteArrayOutputStream());
			return null;
		})).isInstanceOfSatisfying(RateLimitExceededException.class, e -> assertThat(e.getRetryAfterSeconds()).isPositive());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE evento = 'RATE_LIMITED' AND usuario_id = ? "
			+ "AND CAST(detalle AS CHAR) LIKE '%informe-generacion%'", Integer.class, m.auditor.getUserId())).isEqualTo(1);
		// Otro usuario no se ve afectado (el límite es por usuario).
		como(otro, () -> {
			servicio.generarNoPublicado(otro, b.getPublicId(), FormatoInforme.DOCX, new ByteArrayOutputStream());
			return null;
		});
	}

	// ================================================================ copias NP desde el snapshot

	@Test
	void unaVersionPublicadaPuedeGenerarUnaCopiaNpDeUnFormatoQueNoSePublico() throws Exception {
		Mundo m = mundo();
		InformeVersion v1 = publicarMit(m); // solo PDF
		ByteArrayOutputStream xlsx = new ByteArrayOutputStream();
		como(m.auditor, () -> {
			servicio.generarNoPublicado(m.auditor, v1.getPublicId(), FormatoInforme.XLSX, xlsx);
			return null;
		});
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx.toByteArray()))) {
			StringBuilder control = new StringBuilder();
			libro.getSheet("Control").forEach(fila -> fila.forEach(c -> control.append(c.getStringCellValue()).append('\n')));
			assertThat(control.toString()).contains("-MIT-v1-NP").contains("DOCUMENTO NO PUBLICADO");
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE evento = 'INFORME_GENERADO' AND recurso_public_id = ? "
			+ "AND CAST(detalle AS CHAR) LIKE '%SNAPSHOT%'", Integer.class, v1.getPublicId())).isEqualTo(1);
		assertThat(archivosDeBase(m.cuenta, versionDeBase(m.cuenta, v1.getPublicId()).getId())).as("no se guarda").hasSize(1);
	}

	// ================================================================ autorización y aislamiento

	@Test
	void soloUnAuditorAsignadoOperaYElResto_recibeElMismo404() {
		Mundo m = mundo();
		InformeVersion b = tecConNarrativa(m);
		InformeVersion publicada = publicarMit(m);

		// CLIENTE_ADMIN: nada de lo que modifica o genera.
		for (TinyHatPrincipal cliente : List.of(m.cliente, m.clienteOtraOrg)) {
			assertThatThrownBy(() -> como(cliente, () -> servicio.crearBorrador(cliente, m.proyectoUuid, TipoInforme.EJE)))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(cliente, () -> servicio.guardarNarrativa(cliente, b.getPublicId(), "alcanceMetodologia", "x")))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(cliente, () -> servicio.publicar(cliente, b.getPublicId(), EnumSet.of(FormatoInforme.PDF))))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(cliente, () -> servicio.despublicar(cliente, publicada.getPublicId(), "Motivo suficiente de despublicación")))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(cliente, () -> {
				servicio.generarNoPublicado(cliente, publicada.getPublicId(), FormatoInforme.PDF, new ByteArrayOutputStream());
				return null;
			})).isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(cliente, () -> {
				servicio.eliminarBorrador(cliente, b.getPublicId());
				return null;
			})).isInstanceOf(ScopedNotFoundException.class);
		}
		// CLIENTE_ADMIN no ve un borrador (404) ni lo publicado de otra organización.
		assertThatThrownBy(() -> como(m.cliente, () -> servicio.verificar(m.cliente, b.getPublicId()))).isInstanceOf(ScopedNotFoundException.class);
		assertThatThrownBy(() -> como(m.clienteOtraOrg, () -> servicio.verificar(m.clienteOtraOrg, publicada.getPublicId())))
			.isInstanceOf(ScopedNotFoundException.class);
		InformeArchivo archivo = archivosDeBase(m.cuenta, versionDeBase(m.cuenta, publicada.getPublicId()).getId()).get(0);
		assertThatThrownBy(() -> como(m.clienteOtraOrg, () -> servicio.descargar(m.clienteOtraOrg, archivo.getPublicId())))
			.isInstanceOf(ScopedNotFoundException.class);
		assertThat(como(m.cliente, () -> servicio.verificar(m.cliente, publicada.getPublicId())).snapshotSha256()).hasSize(64);

		// AUDITOR sin asignación y AUDITOR de otra cuenta: 404 en todo.
		for (TinyHatPrincipal intruso : List.of(m.auditorSinAsignar, m.auditorAjeno)) {
			assertThatThrownBy(() -> como(intruso, () -> servicio.crearBorrador(intruso, m.proyectoUuid, TipoInforme.EJE)))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(intruso, () -> servicio.guardarNarrativa(intruso, b.getPublicId(), "alcanceMetodologia", "x")))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(intruso, () -> servicio.publicar(intruso, b.getPublicId(), EnumSet.of(FormatoInforme.PDF))))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(intruso, () -> servicio.verificar(intruso, publicada.getPublicId())))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(intruso, () -> servicio.descargar(intruso, archivo.getPublicId())))
				.isInstanceOf(ScopedNotFoundException.class);
			assertThatThrownBy(() -> como(intruso, () -> {
				servicio.previsualizar(intruso, b.getPublicId(), new ByteArrayOutputStream());
				return null;
			})).isInstanceOf(ScopedNotFoundException.class);
		}
		assertThat(versionDeBase(m.cuenta, b.getPublicId()).getEstado()).isEqualTo(EstadoVersion.BORRADOR);
	}

	// ================================================================ utilidades

	private InformeVersion tecConNarrativa(Mundo m) {
		InformeVersion b = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.TEC));
		como(m.auditor, () -> servicio.guardarNarrativa(m.auditor, b.getPublicId(), "alcanceMetodologia", "Alcance de prueba " + MARCA));
		return b;
	}

	/** Un MIT publicado solo en PDF (el Plan de mitigación no tiene secciones obligatorias). */
	private InformeVersion publicarMit(Mundo m) {
		InformeVersion b = como(m.auditor, () -> servicio.crearBorrador(m.auditor, m.proyectoUuid, TipoInforme.MIT));
		como(m.auditor, () -> servicio.publicar(m.auditor, b.getPublicId(), EnumSet.of(FormatoInforme.PDF)));
		return b;
	}

	private <T> T como(TinyHatPrincipal principal, Supplier<T> trabajo) {
		AtomicReference<T> resultado = new AtomicReference<>();
		tenants.execute(principal.getCuentaId(), () -> resultado.set(trabajo.get()));
		return resultado.get();
	}

	private InformeVersion versionDeBase(long cuenta, String publicId) {
		AtomicReference<InformeVersion> v = new AtomicReference<>();
		tenants.execute(cuenta, () -> v.set(versiones.findByPublicIdAndCuentaId(publicId, cuenta).orElseThrow()));
		return v.get();
	}

	private List<InformeArchivo> archivosDeBase(long cuenta, Long versionId) {
		AtomicReference<List<InformeArchivo>> lista = new AtomicReference<>();
		tenants.execute(cuenta, () -> lista.set(archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(versionId, cuenta)));
		return lista.get();
	}

	private int contar(String tabla, long cuenta) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabla + " WHERE cuenta_id = ?", Integer.class, cuenta);
	}

	private List<String> eventosDe(String versionPublicId) {
		return jdbc.queryForList("SELECT evento FROM audit_event WHERE recurso_public_id = ? ORDER BY id", String.class,
			versionPublicId);
	}

	/** Archivos definitivos en disco de la organización de la prueba (cada prueba usa la suya). */
	private static List<Path> archivosEnDisco(Mundo m) {
		Path organizacion = DIRECTORIO.resolve(m.organizacionUuid);
		if (!Files.exists(organizacion)) {
			return List.of();
		}
		try (Stream<Path> todo = Files.walk(organizacion)) {
			return todo.filter(Files::isRegularFile).toList();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<Path> archivosTemporales() throws IOException {
		Path tmp = DIRECTORIO.resolve("tmp");
		if (!Files.exists(tmp)) {
			return List.of();
		}
		try (Stream<Path> todo = Files.list(tmp)) {
			return todo.toList();
		}
	}

	// ---------------------------------------------------------------- mundo de prueba

	private Mundo mundo() {
		// Se parte de un directorio limpio de archivos definitivos: cada prueba usa su propia cuenta y sus propias rutas.
		Mundo m = new Mundo();
		m.cuenta = cuenta("Cuenta Auditora Demo");
		long ajena = cuenta("Cuenta Ajena");
		AtomicReference<Empresa> org = new AtomicReference<>();
		AtomicReference<Empresa> otraOrg = new AtomicReference<>();
		tenants.execute(m.cuenta, () -> {
			org.set(empresa(m.cuenta));
			otraOrg.set(empresa(m.cuenta));
			Auditoria proyecto = proyecto(m.cuenta, org.get());
			m.proyectoId = proyecto.getIdAuditoria();
			m.proyectoUuid = proyecto.getPublicId();
			AreaAuditable red = area(proyecto, "Red");
			AreaAuditable apps = area(proyecto, "Aplicaciones");
			vulnerabilidad(red, "Servicio expuesto a Internet", 9, 8, 6, 8, "Abierta", "Restringir el acceso por VPN");
			vulnerabilidad(apps, TITULO_HALLAZGO, 10, 10, 7, 9, "Abierta", "Usar consultas parametrizadas");
			vulnerabilidad(apps, "Cabeceras HTTP ausentes", 2, 1, 0, 6, "Mitigada", "Configurar las cabeceras");
			vulnerabilidad(apps, "Riesgo asumido", 4, 4, 2, 5, "Aceptada", "El cliente acepta el riesgo");
		});
		m.organizacionId = org.get().getId().longValue();
		m.organizacionUuid = org.get().getPublicId();
		m.auditor = auditor(m, "auditor");
		jdbc.update("INSERT INTO asignacion_auditor_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)",
			m.auditor.getUserId(), m.proyectoId, m.cuenta);
		m.auditorSinAsignar = auditor(m, "sin-asignar");
		m.cliente = cliente(m.cuenta, org.get().getId());
		jdbc.update("INSERT INTO asignacion_cliente_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)", m.cliente.getUserId(), m.proyectoId, m.cuenta);
		m.clienteOtraOrg = cliente(m.cuenta, otraOrg.get().getId());
		long usuarioAjeno = insertarUsuario(ajena, null, "AUDITOR", "ajeno");
		m.auditorAjeno = principal(usuarioAjeno);
		return m;
	}

	private TinyHatPrincipal auditor(Mundo m, String prefijo) {
		TinyHatPrincipal p = principal(insertarUsuario(m.cuenta, null, "AUDITOR", prefijo));
		if ("otro-auditor".equals(prefijo)) {
			jdbc.update("INSERT INTO asignacion_auditor_proyecto (usuario_id, proyecto_id, cuenta_id) VALUES (?, ?, ?)",
				p.getUserId(), m.proyectoId, m.cuenta);
		}
		return p;
	}

	private TinyHatPrincipal cliente(long cuenta, Long organizacion) {
		return principal(insertarUsuario(cuenta, organizacion, "CLIENTE_ADMIN", "cliente"));
	}

	private TinyHatPrincipal principal(long usuarioId) {
		Usuario u = usuarios.findById(usuarioId).orElseThrow();
		return new TinyHatPrincipal(u, true);
	}

	private long insertarUsuario(long cuenta, Long organizacion, String rol, String prefijo) {
		String correo = prefijo + "-" + UUID.randomUUID() + "@example.test";
		jdbc.update("INSERT INTO usuario (cuenta_id, organizacion_id, rol, estado, email_normalizado, password_hash, public_id) "
			+ "VALUES (?, ?, ?, 'ACTIVO', ?, 'test-hash', ?)", cuenta, organizacion, rol, correo, UUID.randomUUID().toString());
		return jdbc.queryForObject("SELECT id FROM usuario WHERE email_normalizado = ?", Long.class, correo);
	}

	private long cuenta(String nombre) {
		String publicId = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO cuenta (public_id, nombre, tipo, estado, creado_en) "
			+ "VALUES (?, ?, 'AUDITOR', 'ACTIVA', UTC_TIMESTAMP(6))", publicId, nombre);
		return jdbc.queryForObject("SELECT id FROM cuenta WHERE public_id = ?", Long.class, publicId);
	}

	private Empresa empresa(long cuenta) {
		Empresa organizacion = new Empresa();
		organizacion.setNombre("Organización " + UUID.randomUUID());
		organizacion.setNit(UUID.randomUUID().toString());
		organizacion.setSector("Tecnología");
		organizacion.setCuentaId(cuenta);
		return empresas.save(organizacion);
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
		return proyectos.save(proyecto);
	}

	private AreaAuditable area(Auditoria proyecto, String nombre) {
		AreaAuditable area = new AreaAuditable();
		area.setNombre(nombre);
		area.setDescripcion("Descripción de " + nombre);
		area.setAuditoria(proyecto);
		area.setCuentaId(proyecto.getCuentaId());
		return areas.save(area);
	}

	private void vulnerabilidad(
		AreaAuditable area, String nombre, int c, int i, int a, int probabilidad, String estado, String plan
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
		v.setFechaDeteccion(LocalDate.now());
		v.setAreaAuditable(area);
		v.setCuentaId(area.getCuentaId());
		vulnerabilidades.save(v);
	}

	private static final class Mundo {
		long cuenta;
		long proyectoId;
		long organizacionId;
		String organizacionUuid;
		String proyectoUuid;
		TinyHatPrincipal auditor;
		TinyHatPrincipal auditorSinAsignar;
		TinyHatPrincipal auditorAjeno;
		TinyHatPrincipal cliente;
		TinyHatPrincipal clienteOtraOrg;
	}
}
