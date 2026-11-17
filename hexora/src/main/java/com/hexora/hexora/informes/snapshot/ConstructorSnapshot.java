package com.hexora.hexora.informes.snapshot;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Area;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Cia;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Cve;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.DatosAuditor;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.DatosInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.DatosOrganizacion;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.DatosProyecto;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaAnexoA;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaTrazabilidad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.FilaAreaSeveridad;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Metricas;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Riesgo;
import com.hexora.hexora.informes.validacion.InformeValidacionException;
import com.hexora.hexora.riesgo.EscalaRiesgo;
import com.hexora.hexora.riesgo.Severidad;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Construye el {@link InformeSnapshot} a partir de datos ya leídos (sin acceso a base de datos ni a red): clasifica,
 * deriva severidad y riesgo, separa el Anexo A y calcula todas las métricas, órdenes, fases y referencias.
 * {@link EnsambladorSnapshot} lee los datos y llama a esta clase.
 */
public final class ConstructorSnapshot {

    /** Estado de la aplicación que va al Anexo A (riesgo aceptado); "falso positivo" no existe como estado. */
    public static final String ESTADO_ACEPTADA = "Aceptada";

    private static final Pattern CODIGO_CVE = Pattern.compile("^CVE-\\d{4}-\\d{4,19}$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_REFERENCIAS_POR_HALLAZGO = 20;

    private ConstructorSnapshot() {
    }

    /** Área del proyecto; {@code clave} la distingue aunque dos áreas se llamen igual. */
    public record FuenteArea(String clave, String nombre, String descripcion) {
    }

    /** Datos del CVE tomados de la caché local. */
    public record FuenteCve(BigDecimal cvss, LocalDateTime publicadoUtc, String referenciasParche) {
    }

    /** Vulnerabilidad tal como está en la aplicación (valores 0-10). */
    public record FuenteVulnerabilidad(
        String publicId,
        String areaClave,
        String titulo,
        int confidencialidad,
        int integridad,
        int disponibilidad,
        Integer probabilidad0a10,
        String estado,
        String mitigacion,
        String cveCodigo,
        FuenteCve cve
    ) {
    }

    public record Entrada(
        TipoInforme tipo,
        int numeroVersion,
        String estadoDocumento,
        boolean noPublicado,
        int anio,
        String organizacionUuid,
        String proyectoUuid,
        String organizacionNombre,
        String proyectoNombre,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String auditorNombre,
        List<FuenteArea> areas,
        List<FuenteVulnerabilidad> vulnerabilidades,
        Map<String, String> narrativa,
        List<EntradaTrazabilidad> trazabilidad,
        Instant generadoEn,
        String plantillaVersion,
        int maxHallazgos,
        ReferenciasApa referencias
    ) {
    }

    public static InformeSnapshot construir(Entrada entrada) {
        if (entrada.vulnerabilidades().size() > entrada.maxHallazgos()) {
            throw new InformeValidacionException("El proyecto tiene " + entrada.vulnerabilidades().size()
                + " vulnerabilidades y el máximo por informe es " + entrada.maxHallazgos() + ".");
        }

        List<FuenteArea> areasOrdenadas = entrada.areas().stream()
            .sorted(Comparator.comparing(FuenteArea::nombre, OrdenHallazgos.AREAS).thenComparing(FuenteArea::clave))
            .toList();
        Map<String, String> nombreDeArea = new LinkedHashMap<>();
        areasOrdenadas.forEach(area -> nombreDeArea.put(area.clave(), area.nombre()));

        Map<String, List<Hallazgo>> cuerpoPorArea = new LinkedHashMap<>();
        areasOrdenadas.forEach(area -> cuerpoPorArea.put(area.clave(), new ArrayList<>()));
        List<EntradaAnexoA> anexoA = new ArrayList<>();
        int aceptados = 0;

        for (FuenteVulnerabilidad fuente : entrada.vulnerabilidades()) {
            String nombreArea = nombreDeArea.get(fuente.areaClave());
            if (nombreArea == null) {
                throw new InformeValidacionException("Una vulnerabilidad pertenece a un área que no es del proyecto.");
            }
            Hallazgo hallazgo = hallazgo(fuente);
            if (esAceptada(fuente.estado())) {
                aceptados++;
                anexoA.add(new EntradaAnexoA(hallazgo.codigo(), hallazgo.titulo(), nombreArea, hallazgo.severidad(),
                    EntradaAnexoA.ACEPTADO));
            } else {
                cuerpoPorArea.get(fuente.areaClave()).add(hallazgo);
            }
        }

        List<Area> areas = new ArrayList<>();
        for (FuenteArea area : areasOrdenadas) {
            List<Hallazgo> hallazgos = cuerpoPorArea.get(area.clave()).stream().sorted(OrdenHallazgos.FICHAS_TEC).toList();
            areas.add(new Area(area.nombre(), area.descripcion(), hallazgos));
        }
        anexoA.sort(Comparator.comparing(EntradaAnexoA::area, OrdenHallazgos.AREAS).thenComparing(EntradaAnexoA::codigo));

        Metricas metricas = metricas(areas, aceptados);
        List<Cve> cvesDelCuerpo = areas.stream()
            .flatMap(area -> area.hallazgos().stream())
            .flatMap(hallazgo -> hallazgo.cves().stream())
            .toList();

        String id = GeneradorIdInforme.generar(entrada.anio(), entrada.organizacionUuid(), entrada.proyectoUuid(),
            entrada.tipo(), entrada.numeroVersion(), entrada.noPublicado());

        return new InformeSnapshot(
            InformeSnapshot.VERSION_ESQUEMA,
            entrada.plantillaVersion(),
            FormatoFechas.iso(entrada.generadoEn()),
            new DatosInforme(entrada.tipo().name(), entrada.numeroVersion(), id, entrada.estadoDocumento()),
            new DatosOrganizacion(entrada.organizacionNombre()),
            new DatosProyecto(entrada.proyectoNombre(), FormatoFechas.isoFecha(entrada.fechaInicio()),
                FormatoFechas.isoFecha(entrada.fechaFin())),
            new DatosAuditor(entrada.auditorNombre()),
            metricas,
            areas,
            anexoA,
            entrada.tipo() == TipoInforme.FIN ? entrada.trazabilidad() : List.of(),
            entrada.narrativa(),
            entrada.referencias().construir(cvesDelCuerpo));
    }

    static boolean esAceptada(String estado) {
        return estado != null && ESTADO_ACEPTADA.equalsIgnoreCase(estado.strip());
    }

    private static Hallazgo hallazgo(FuenteVulnerabilidad v) {
        int impacto = EscalaRiesgo.impacto(v.confidencialidad(), v.integridad(), v.disponibilidad());
        Severidad severidad = Severidad.desdeImpacto(impacto);
        Integer probabilidad = v.probabilidad0a10() == null ? null : EscalaRiesgo.probabilidad(v.probabilidad0a10());
        Riesgo riesgo = probabilidad == null
            ? null
            : new Riesgo(EscalaRiesgo.puntaje(probabilidad, impacto), EscalaRiesgo.nivel(probabilidad, impacto).name());
        return new Hallazgo(
            GeneradorIdInforme.codigoHallazgo(v.publicId()),
            v.titulo(),
            textoONulo(v.mitigacion()),
            v.estado(),
            severidad.name(),
            new Cia(v.confidencialidad(), v.integridad(), v.disponibilidad()),
            probabilidad,
            impacto,
            riesgo,
            cves(v),
            referencias(v.cve()),
            severidad.faseSugerida().name());
    }

    private static List<Cve> cves(FuenteVulnerabilidad v) {
        String codigo = v.cveCodigo() == null ? "" : v.cveCodigo().strip().toUpperCase(Locale.ROOT);
        if (!CODIGO_CVE.matcher(codigo).matches()) {
            return List.of();
        }
        FuenteCve cache = v.cve();
        return List.of(new Cve(
            codigo,
            cache == null ? null : cache.cvss(),
            cache == null || cache.publicadoUtc() == null ? null : cache.publicadoUtc().toLocalDate().toString(),
            "https://nvd.nist.gov/vuln/detail/" + codigo));
    }

    /** Referencias de parche del CVE (solo http/https), como texto, sin enlaces. */
    private static List<String> referencias(FuenteCve cve) {
        if (cve == null || cve.referenciasParche() == null || cve.referenciasParche().isBlank()) {
            return List.of();
        }
        return cve.referenciasParche().lines()
            .map(String::strip)
            .filter(linea -> linea.startsWith("https://") || linea.startsWith("http://"))
            .distinct()
            .limit(MAX_REFERENCIAS_POR_HALLAZGO)
            .toList();
    }

    private static Metricas metricas(List<Area> areas, int aceptados) {
        Map<String, Integer> porSeveridad = conteoVacio();
        Map<String, Integer> porEstado = new TreeMap<>();
        int[][] calor = new int[EscalaRiesgo.MAXIMO][EscalaRiesgo.MAXIMO];
        List<FilaAreaSeveridad> porArea = new ArrayList<>();
        int total = 0;
        int sinProbabilidad = 0;

        for (Area area : areas) {
            Map<String, Integer> delArea = conteoVacio();
            for (Hallazgo hallazgo : area.hallazgos()) {
                total++;
                porSeveridad.merge(hallazgo.severidad(), 1, Integer::sum);
                delArea.merge(hallazgo.severidad(), 1, Integer::sum);
                porEstado.merge(hallazgo.estado(), 1, Integer::sum);
                if (hallazgo.probabilidad() == null) {
                    sinProbabilidad++;
                } else {
                    calor[hallazgo.probabilidad() - 1][hallazgo.impacto15() - 1]++;
                }
            }
            porArea.add(new FilaAreaSeveridad(area.nombre(), delArea));
        }

        List<List<Integer>> mapaCalor = new ArrayList<>();
        for (int[] fila : calor) {
            List<Integer> celdas = new ArrayList<>();
            for (int valor : fila) {
                celdas.add(valor);
            }
            mapaCalor.add(celdas);
        }
        // "Falso positivo" no existe como estado en la aplicación: siempre 0 (decisión D5).
        return new Metricas(porSeveridad, porArea, porEstado, mapaCalor, sinProbabilidad, total, areas.size(),
            aceptados, 0);
    }

    /** Las cinco severidades, todas con cero (el JSON canónico las ordena por nombre). */
    private static Map<String, Integer> conteoVacio() {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (Severidad severidad : Severidad.values()) {
            conteo.put(severidad.name(), 0);
        }
        return conteo;
    }

    private static String textoONulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
