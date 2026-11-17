package com.hexora.hexora.informes.snapshot;

import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.riesgo.EscalaRiesgo.NivelRiesgo;
import com.hexora.hexora.riesgo.Severidad;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Órdenes deterministas de §4.3. El snapshot guarda las áreas por nombre y los hallazgos en el orden de las fichas
 * técnicas; el resto de órdenes los calculan los renderizadores con estos comparadores, de modo que dos renderizadores
 * nunca discrepan.
 *
 * <pre>
 *   Áreas                nombre ascendente
 *   Fichas TEC (en área) severidad desc, suma CIA desc, código asc
 *   MIT                  severidad desc, suma CIA desc, área asc, código asc
 *   MR (Registro)        nivel de riesgo desc, puntaje P x I desc, severidad desc, código asc
 *   Top 5 de EJE         severidad desc, puntaje P x I desc (nulos al final), suma CIA desc, código asc
 * </pre>
 */
public final class OrdenHallazgos {

    /** Un hallazgo junto con el nombre de su área, para los órdenes que cruzan áreas. */
    public record HallazgoDeArea(String area, Hallazgo hallazgo) {
    }

    public static final int TAMANO_TOP_EJECUTIVO = 5;

    /** Áreas: nombre ascendente, sin distinguir mayúsculas ni tildes. */
    public static final Comparator<String> AREAS = Comparator
        .comparing(OrdenHallazgos::claveDeTexto)
        .thenComparing(Comparator.naturalOrder());

    /** Fichas del informe técnico, dentro de cada área. */
    public static final Comparator<Hallazgo> FICHAS_TEC = Comparator
        .comparingInt(OrdenHallazgos::posicionSeveridad)
        .thenComparing(Comparator.comparingInt(Hallazgo::sumaCia).reversed())
        .thenComparing(Hallazgo::codigo);

    /** Plan de mitigación: por severidad. */
    public static final Comparator<HallazgoDeArea> MITIGACION = Comparator
        .comparing(HallazgoDeArea::hallazgo, Comparator
            .comparingInt(OrdenHallazgos::posicionSeveridad)
            .thenComparing(Comparator.comparingInt(Hallazgo::sumaCia).reversed()))
        .thenComparing(HallazgoDeArea::area, AREAS)
        .thenComparing(par -> par.hallazgo().codigo());

    /** Registro de la matriz de riesgo: por nivel de riesgo, no solo por severidad. */
    public static final Comparator<HallazgoDeArea> MATRIZ_RIESGO = Comparator
        .comparing(HallazgoDeArea::hallazgo, Comparator
            .comparingInt(OrdenHallazgos::posicionNivelRiesgo)
            .thenComparing(Comparator.comparingInt(OrdenHallazgos::puntajeONulo).reversed())
            .thenComparingInt(OrdenHallazgos::posicionSeveridad))
        .thenComparing(par -> par.hallazgo().codigo());

    /** Los cinco riesgos principales del informe ejecutivo. */
    public static final Comparator<HallazgoDeArea> TOP_EJECUTIVO = Comparator
        .comparing(HallazgoDeArea::hallazgo, Comparator
            .comparingInt(OrdenHallazgos::posicionSeveridad)
            .thenComparing(Comparator.comparingInt(OrdenHallazgos::puntajeONulo).reversed())
            .thenComparing(Comparator.comparingInt(Hallazgo::sumaCia).reversed()))
        .thenComparing(par -> par.hallazgo().codigo());

    private OrdenHallazgos() {
    }

    /** Todos los hallazgos del cuerpo con su área, en el orden del snapshot. */
    public static List<HallazgoDeArea> aplanar(InformeSnapshot snapshot) {
        return snapshot.areas().stream()
            .flatMap(area -> area.hallazgos().stream().map(hallazgo -> new HallazgoDeArea(area.nombre(), hallazgo)))
            .toList();
    }

    public static List<HallazgoDeArea> paraMitigacion(InformeSnapshot snapshot) {
        return aplanar(snapshot).stream().sorted(MITIGACION).toList();
    }

    public static List<HallazgoDeArea> paraMatrizRiesgo(InformeSnapshot snapshot) {
        return aplanar(snapshot).stream().sorted(MATRIZ_RIESGO).toList();
    }

    /** Hasta cinco riesgos principales (menos si hay menos hallazgos). */
    public static List<HallazgoDeArea> topEjecutivo(InformeSnapshot snapshot) {
        return aplanar(snapshot).stream().sorted(TOP_EJECUTIVO).limit(TAMANO_TOP_EJECUTIVO).toList();
    }

    private static int posicionSeveridad(Hallazgo hallazgo) {
        return Severidad.valueOf(hallazgo.severidad()).posicionDeGravedad();
    }

    /** Un hallazgo sin riesgo (sin probabilidad) va después de todos los que lo tienen. */
    private static int posicionNivelRiesgo(Hallazgo hallazgo) {
        return hallazgo.riesgo() == null
            ? NivelRiesgo.values().length
            : NivelRiesgo.valueOf(hallazgo.riesgo().nivel()).posicionDeCriticidad();
    }

    /** Puntaje P x I; los nulos valen 0 y, al ordenar de mayor a menor, quedan al final. */
    private static int puntajeONulo(Hallazgo hallazgo) {
        return hallazgo.riesgo() == null ? 0 : hallazgo.riesgo().puntaje();
    }

    private static String claveDeTexto(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }
}
