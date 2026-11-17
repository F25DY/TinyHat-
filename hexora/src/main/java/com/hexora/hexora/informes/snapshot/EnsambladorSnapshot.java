package com.hexora.hexora.informes.snapshot;

import com.hexora.hexora.informes.EstadoVersion;
import com.hexora.hexora.informes.InformesProperties;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteArea;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteCve;
import com.hexora.hexora.informes.snapshot.ConstructorSnapshot.FuenteVulnerabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaTrazabilidad;
import com.hexora.hexora.model.AreaAuditable;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Cuenta;
import com.hexora.hexora.model.Cve;
import com.hexora.hexora.model.Informe;
import com.hexora.hexora.model.InformeArchivo;
import com.hexora.hexora.model.InformeVersion;
import com.hexora.hexora.model.Vulnerabilidad;
import com.hexora.hexora.repository.AreaAuditableRepository;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.CveRepository;
import com.hexora.hexora.repository.InformeArchivoRepository;
import com.hexora.hexora.repository.InformeRepository;
import com.hexora.hexora.repository.InformeVersionRepository;
import com.hexora.hexora.repository.VulnerabilidadRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee los datos de un proyecto y arma su {@link InformeSnapshot}.
 *
 * <p>El llamador debe pasar un proyecto ya resuelto con {@code PermissionService.requireProyecto} (cuenta y asignación
 * comprobadas); cada consulta de esta clase filtra además por la cuenta del proyecto, de modo que áreas,
 * vulnerabilidades o informes de otra cuenta no pueden entrar en un snapshot. Los datos de CVE salen <b>solo de la
 * caché local</b>: esta clase no tiene ninguna dependencia que acceda a la red ni al NVD.
 */
@Component
public class EnsambladorSnapshot {

    private static final Pattern CODIGO_CVE = Pattern.compile("^CVE-\\d{4}-\\d{4,19}$", Pattern.CASE_INSENSITIVE);

    private final AreaAuditableRepository areas;
    private final VulnerabilidadRepository vulnerabilidades;
    private final CveRepository cves;
    private final CuentaRepository cuentas;
    private final InformeRepository informes;
    private final InformeVersionRepository versiones;
    private final InformeArchivoRepository archivos;
    private final InformesProperties propiedades;
    private final ReferenciasApa referencias;
    private final Clock reloj;

    public EnsambladorSnapshot(
        AreaAuditableRepository areas,
        VulnerabilidadRepository vulnerabilidades,
        CveRepository cves,
        CuentaRepository cuentas,
        InformeRepository informes,
        InformeVersionRepository versiones,
        InformeArchivoRepository archivos,
        InformesProperties propiedades,
        Clock reloj
    ) {
        this.areas = areas;
        this.vulnerabilidades = vulnerabilidades;
        this.cves = cves;
        this.cuentas = cuentas;
        this.informes = informes;
        this.versiones = versiones;
        this.archivos = archivos;
        this.propiedades = propiedades;
        this.referencias = ReferenciasApa.desdeClasspath();
        this.reloj = reloj;
    }

    /**
     * @param estado       estado que lleva el documento: PUBLICADO al publicar, BORRADOR en una copia de trabajo
     * @param noPublicado  true para copias de trabajo (el ID lleva el sufijo -NP)
     * @param anioSerie    año de creación de la serie (forma parte del ID)
     * @param narrativa    mapa de sección a Markdown original
     */
    @Transactional(readOnly = true)
    public InformeSnapshot ensamblar(
        Auditoria proyecto, TipoInforme tipo, int numeroVersion, EstadoVersion estado, boolean noPublicado,
        int anioSerie, Map<String, String> narrativa
    ) {
        return ensamblar(proyecto, tipo, numeroVersion, estado, noPublicado, anioSerie, narrativa, Instant.now(reloj));
    }

    @Transactional(readOnly = true)
    public InformeSnapshot ensamblar(
        Auditoria proyecto, TipoInforme tipo, int numeroVersion, EstadoVersion estado, boolean noPublicado,
        int anioSerie, Map<String, String> narrativa, Instant generadoEn
    ) {
        long cuentaId = proyecto.getCuentaId();
        List<AreaAuditable> areasDelProyecto = areas.findByAuditoriaAndCuentaId(proyecto, cuentaId);

        List<FuenteArea> fuentesArea = new ArrayList<>();
        List<FuenteVulnerabilidad> fuentes = new ArrayList<>();
        Map<String, Optional<Cve>> cache = new HashMap<>();
        for (AreaAuditable area : areasDelProyecto) {
            fuentesArea.add(new FuenteArea(area.getPublicId(), area.getNombre(), area.getDescripcion()));
            for (Vulnerabilidad v : vulnerabilidades.findByAreaAuditableAndCuentaId(area, cuentaId)) {
                fuentes.add(fuente(area, v, cache));
            }
        }

        String auditor = cuentas.findById(cuentaId).map(Cuenta::getNombre).orElse("");
        return ConstructorSnapshot.construir(new ConstructorSnapshot.Entrada(
            tipo,
            numeroVersion,
            estado.name(),
            noPublicado,
            anioSerie,
            proyecto.getEmpresa().getPublicId(),
            proyecto.getPublicId(),
            proyecto.getEmpresa().getNombre(),
            proyecto.getNombre(),
            proyecto.getFechaInico(),
            proyecto.getFechaFin(),
            auditor,
            fuentesArea,
            fuentes,
            narrativa,
            tipo == TipoInforme.FIN ? trazabilidad(proyecto) : List.of(),
            generadoEn,
            propiedades.getPlantillaVersion(),
            propiedades.getMaxHallazgos(),
            referencias,
            historial(proyecto, tipo, numeroVersion, estado, generadoEn)));
    }

    private FuenteVulnerabilidad fuente(AreaAuditable area, Vulnerabilidad v, Map<String, Optional<Cve>> cache) {
        String codigo = v.getCve() == null ? null : v.getCve().strip().toUpperCase(Locale.ROOT);
        FuenteCve fuenteCve = null;
        if (codigo != null && CODIGO_CVE.matcher(codigo).matches()) {
            fuenteCve = cache.computeIfAbsent(codigo, cves::findByCodigoCve)
                .map(cve -> new FuenteCve(cve.getPuntajeCvss(), cve.getFechaPublicacion(), cve.getReferenciasParche()))
                .orElse(null);
        }
        return new FuenteVulnerabilidad(
            v.getPublicId(),
            area.getPublicId(),
            v.getNombre(),
            v.getConfidencialidad(),
            v.getIntegridad(),
            v.getDisponibilidad(),
            v.getProbabilidad(),
            v.getEstado(),
            v.getPlanMitigacion(),
            codigo,
            fuenteCve);
    }

    /**
     * Versiones de la serie hasta la que se está generando. La versión en curso lleva el estado que tiene el documento
     * (al publicar, la base todavía la ve como borrador); las anteriores salen de la base.
     */
    private List<InformeSnapshot.EntradaHistorial> historial(
        Auditoria proyecto, TipoInforme tipo, int numeroVersion, EstadoVersion estado, Instant generadoEn
    ) {
        long cuentaId = proyecto.getCuentaId();
        List<InformeSnapshot.EntradaHistorial> entradas = new ArrayList<>();
        Optional<Informe> serie = informes.findByProyectoIdAndTipoAndCuentaId(
            proyecto.getIdAuditoria(), tipo, cuentaId);
        if (serie.isPresent()) {
            for (InformeVersion version : versiones.findByInformeIdAndCuentaIdOrderByNumeroDesc(
                serie.get().getId(), cuentaId)) {
                if (version.getNumero() < numeroVersion) {
                    entradas.add(new InformeSnapshot.EntradaHistorial(
                        version.getNumero(),
                        version.getEstado().name(),
                        version.getPublicadoEn() == null ? null : FormatoFechas.isoDesdeUtc(version.getPublicadoEn()),
                        version.getDespublicadoEn() == null ? null
                            : FormatoFechas.isoDesdeUtc(version.getDespublicadoEn()),
                        version.getMotivoDespublicacion()));
                }
            }
        }
        entradas.add(new InformeSnapshot.EntradaHistorial(numeroVersion, estado.name(),
            estado == EstadoVersion.PUBLICADO ? FormatoFechas.iso(generadoEn) : null, null, null));
        entradas.sort(Comparator.comparingInt(InformeSnapshot.EntradaHistorial::version));
        return entradas;
    }

    /** Versiones publicadas vigentes de los otros tipos del proyecto, con el hash de cada archivo. */
    private List<EntradaTrazabilidad> trazabilidad(Auditoria proyecto) {
        long cuentaId = proyecto.getCuentaId();
        Map<Long, Informe> serieDe = new HashMap<>();
        informes.findByProyectoIdAndCuentaIdOrderByTipoAsc(proyecto.getIdAuditoria(), cuentaId)
            .forEach(informe -> serieDe.put(informe.getId(), informe));

        List<EntradaTrazabilidad> entradas = new ArrayList<>();
        for (InformeVersion version : versiones.findPublicadasDeProyecto(cuentaId, proyecto.getIdAuditoria())) {
            Informe serie = serieDe.get(version.getInformeId());
            if (serie == null || serie.getTipo() == TipoInforme.FIN) {
                continue;
            }
            String id = GeneradorIdInforme.generar(serie.getAnio(), proyecto.getEmpresa().getPublicId(),
                proyecto.getPublicId(), serie.getTipo(), version.getNumero(), false);
            for (InformeArchivo archivo : archivos.findByVersionIdAndCuentaIdOrderByFormatoAsc(version.getId(), cuentaId)) {
                entradas.add(new EntradaTrazabilidad(id, serie.getTipo().name(), archivo.getFormato().name(),
                    archivo.getSha256(), FormatoFechas.isoDesdeUtc(version.getPublicadoEn())));
            }
        }
        entradas.sort(Comparator.comparing(EntradaTrazabilidad::tipo)
            .thenComparing(EntradaTrazabilidad::formato));
        return entradas;
    }
}
