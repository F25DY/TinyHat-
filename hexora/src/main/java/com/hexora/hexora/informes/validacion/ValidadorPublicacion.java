package com.hexora.hexora.informes.validacion;

import com.hexora.hexora.informes.InformesProperties;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.TipoInforme.SeccionNarrativa;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Area;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Condiciones para publicar (§4.5), validadas <b>sobre el snapshot</b>, no sobre los datos vivos:
 * <ol>
 *   <li>El proyecto tiene alcance definido. La aplicación no guarda el alcance como campo (decisión D5): se exigen
 *       las fechas del proyecto y al menos un área; en TEC y FIN el alcance lo cubre la sección obligatoria
 *       «Alcance y metodología» (condición 4).</li>
 *   <li>Cada hallazgo del cuerpo tiene severidad, estado y mitigación.</li>
 *   <li>No hay hallazgos sin clasificar (severidad o estado no reconocidos).</li>
 *   <li>Están completas las secciones narrativas obligatorias del tipo.</li>
 *   <li>MR y FIN: todos los hallazgos del cuerpo tienen probabilidad.</li>
 *   <li>Se respetan los límites de tamaño.</li>
 * </ol>
 */
@Component
public class ValidadorPublicacion {

    /** Estados del cuerpo del informe; "Aceptada" va al Anexo A y no se valida aquí. */
    static final Set<String> ESTADOS_RECONOCIDOS = Set.of("Abierta", "En Progreso", "Mitigada");

    /** Para no devolver listas interminables: se detallan los primeros y se resume el resto. */
    private static final int MAX_MENSAJES_DETALLADOS = 25;

    private final int maxHallazgos;
    private final int maxNarrativaCaracteres;

    @Autowired
    public ValidadorPublicacion(InformesProperties propiedades) {
        this(propiedades.getMaxHallazgos(), propiedades.getMaxNarrativaCaracteres());
    }

    public ValidadorPublicacion(int maxHallazgos, int maxNarrativaCaracteres) {
        this.maxHallazgos = maxHallazgos;
        this.maxNarrativaCaracteres = maxNarrativaCaracteres;
    }

    public ResultadoValidacion validar(InformeSnapshot snapshot) {
        TipoInforme tipo = TipoInforme.valueOf(snapshot.informe().tipo());
        List<String> faltantes = new ArrayList<>();

        validarProyecto(snapshot, faltantes);
        validarHallazgos(snapshot, tipo, faltantes);
        validarNarrativa(snapshot, tipo, faltantes);
        validarLimites(snapshot, faltantes);
        return new ResultadoValidacion(faltantes);
    }

    private static void validarProyecto(InformeSnapshot snapshot, List<String> faltantes) {
        var proyecto = snapshot.proyecto();
        boolean sinFechas = proyecto.fechaInicio() == null || proyecto.fechaFin() == null;
        if (sinFechas || snapshot.areas().isEmpty()) {
            faltantes.add("El proyecto no tiene el alcance definido: complete las fechas y registre al menos un área.");
        }
    }

    private static void validarHallazgos(InformeSnapshot snapshot, TipoInforme tipo, List<String> faltantes) {
        boolean exigeProbabilidad = tipo == TipoInforme.MR || tipo == TipoInforme.FIN;
        List<String> mensajes = new ArrayList<>();
        for (Area area : snapshot.areas()) {
            for (Hallazgo hallazgo : area.hallazgos()) {
                String quien = "El hallazgo " + hallazgo.codigo() + " (" + area.nombre() + ")";
                if (hallazgo.severidad() == null || hallazgo.severidad().isBlank()) {
                    mensajes.add(quien + " no tiene severidad.");
                }
                if (hallazgo.estado() == null || hallazgo.estado().isBlank()) {
                    mensajes.add(quien + " no tiene estado.");
                } else if (!ESTADOS_RECONOCIDOS.contains(hallazgo.estado())) {
                    mensajes.add(quien + " está sin clasificar: su estado no es reconocido.");
                }
                if (hallazgo.mitigacion() == null || hallazgo.mitigacion().isBlank()) {
                    mensajes.add(quien + " no tiene plan de mitigación.");
                }
                if (exigeProbabilidad && hallazgo.probabilidad() == null) {
                    mensajes.add(quien + " no tiene probabilidad.");
                }
            }
        }
        resumir(mensajes, faltantes);
    }

    private void validarNarrativa(InformeSnapshot snapshot, TipoInforme tipo, List<String> faltantes) {
        for (SeccionNarrativa seccion : tipo.secciones()) {
            String texto = snapshot.narrativa().get(seccion.clave());
            if (seccion.obligatoria() && (texto == null || texto.isBlank())) {
                faltantes.add("Falta la sección obligatoria «" + seccion.etiqueta() + "».");
            }
        }
        for (var entrada : snapshot.narrativa().entrySet()) {
            if (!tipo.admiteSeccion(entrada.getKey())) {
                faltantes.add("La sección «" + entrada.getKey() + "» no corresponde a este tipo de informe.");
            } else if (entrada.getValue() != null && entrada.getValue().length() > maxNarrativaCaracteres) {
                faltantes.add("La sección «" + etiquetaDe(tipo, entrada.getKey()) + "» supera el máximo de "
                    + maxNarrativaCaracteres + " caracteres.");
            }
        }
    }

    private void validarLimites(InformeSnapshot snapshot, List<String> faltantes) {
        int total = snapshot.metricas().totalHallazgos() + snapshot.metricas().aceptados();
        if (total > maxHallazgos) {
            faltantes.add("El proyecto tiene " + total + " vulnerabilidades y el máximo por informe es "
                + maxHallazgos + ".");
        }
    }

    private static String etiquetaDe(TipoInforme tipo, String clave) {
        return tipo.secciones().stream().filter(s -> s.clave().equals(clave)).map(SeccionNarrativa::etiqueta)
            .findFirst().orElse(clave);
    }

    private static void resumir(List<String> mensajes, List<String> destino) {
        if (mensajes.size() <= MAX_MENSAJES_DETALLADOS) {
            destino.addAll(mensajes);
            return;
        }
        destino.addAll(mensajes.subList(0, MAX_MENSAJES_DETALLADOS));
        destino.add("… y " + (mensajes.size() - MAX_MENSAJES_DETALLADOS) + " faltantes más en los hallazgos.");
    }
}
