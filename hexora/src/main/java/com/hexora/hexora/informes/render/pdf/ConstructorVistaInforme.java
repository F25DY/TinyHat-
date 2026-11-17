package com.hexora.hexora.informes.render.pdf;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.render.pdf.VistaInforme.AreaFichas;
import com.hexora.hexora.informes.render.pdf.VistaInforme.Celda;
import com.hexora.hexora.informes.render.pdf.VistaInforme.CeldaCalor;
import com.hexora.hexora.informes.render.pdf.VistaInforme.CveVista;
import com.hexora.hexora.informes.render.pdf.VistaInforme.Documento;
import com.hexora.hexora.informes.render.pdf.VistaInforme.EntradaIndice;
import com.hexora.hexora.informes.render.pdf.VistaInforme.Ficha;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaAnexoA;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaArea;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaCalor;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaConteo;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaHistorial;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaMatriz;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaMatrizNivel;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaMitigacion;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaNivel;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaResumen;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaRiesgoPrincipal;
import com.hexora.hexora.informes.render.pdf.VistaInforme.FilaTraza;
import com.hexora.hexora.informes.render.pdf.VistaInforme.Referencia;
import com.hexora.hexora.informes.render.pdf.VistaInforme.Segmento;
import com.hexora.hexora.informes.render.pdf.VistaInforme.SegmentoTexto;
import com.hexora.hexora.informes.render.pdf.VistaInforme.Seccion;
import com.hexora.hexora.informes.snapshot.FormatoFechas;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Area;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.EntradaAnexoA;
import com.hexora.hexora.informes.snapshot.InformeSnapshot.Hallazgo;
import com.hexora.hexora.informes.snapshot.OrdenHallazgos;
import com.hexora.hexora.informes.snapshot.OrdenHallazgos.HallazgoDeArea;
import com.hexora.hexora.informes.snapshot.SerializadorSnapshot;
import com.hexora.hexora.informes.validacion.ResultadoValidacion;
import com.hexora.hexora.riesgo.EscalaRiesgo;
import com.hexora.hexora.riesgo.EscalaRiesgo.NivelRiesgo;
import com.hexora.hexora.riesgo.Severidad;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Prepara el modelo de las plantillas del PDF a partir del snapshot: toda cifra, orden, color y texto se decide aquí,
 * en Java, y las plantillas solo lo pintan. Los textos de usuario nunca se interpretan como marcado.
 */
@Component
public class ConstructorVistaInforme {

    static final String LOGO = "classpath:informes/pdf/img/logo-tinyhat.png";
    static final String RAYA = "—";

    private static final String[] IMPACTO = {"Muy bajo", "Bajo", "Medio", "Alto", "Muy alto"};

    private final MarkdownSaneado markdown;
    private final TextosInforme textos;

    public ConstructorVistaInforme(MarkdownSaneado markdown, TextosInforme textos) {
        this.markdown = markdown;
        this.textos = textos;
    }

    public VistaInforme construir(InformeSnapshot s, OpcionesRender opciones) {
        TipoInforme tipo = TipoInforme.valueOf(s.informe().tipo());
        boolean np = opciones.noPublicado();
        List<HallazgoDeArea> todos = OrdenHallazgos.aplanar(s);
        int aceptados = s.metricas().aceptados();

        return new VistaInforme(
            documento(s, tipo, np),
            historial(s),
            aviso(textos.aviso("referencia")),
            aviso(textos.aviso("alcance")),
            np ? aviso(textos.aviso("no-publicado")) : null,
            aceptados + s.metricas().falsosPositivos() > 0
                ? aviso(textos.avisoAnexoA(aceptados, s.metricas().falsosPositivos())) : null,
            aviso(textos.aviso("fase-sugerida")),
            aviso(textos.aviso("orden-mr")),
            secciones(tipo, s.narrativa(), np),
            tipo == TipoInforme.TEC ? indice(s, aceptados > 0, np) : List.of(),
            new Seccion(null, markdown.analizar(textos.escalaCalificacion()).html(), true, false),
            matrizNiveles(),
            new Seccion(null, markdown.analizar(textos.glosario()).html(), true, false),
            conteoSeveridad(s),
            s.metricas().totalHallazgos(),
            etiquetasSeveridad(),
            areaSeveridad(s),
            riesgosPrincipales(s, np),
            resumen(todos),
            fichas(s, np),
            mitigacion(s, np),
            registroMatriz(s, np),
            List.of(IMPACTO),
            mapaCalor(s),
            s.metricas().sinProbabilidad(),
            conteoNiveles(todos),
            s.anexoA().stream().map(this::filaAnexoA).toList(),
            s.trazabilidad().stream().map(t -> new FilaTraza(t.id(), TipoInforme.valueOf(t.tipo()).nombre(),
                t.formato(), t.sha256(), FormatoFechas.fechaLarga(t.publicadoEn()))).toList(),
            referencias(s.referenciasApa()));
    }

    // ---------------------------------------------------------------- documento

    private Documento documento(InformeSnapshot s, TipoInforme tipo, boolean np) {
        String huella = SerializadorSnapshot.huella(s);
        String corto = huella.substring(0, 12);
        String pie = s.informe().id() + " · CONFIDENCIAL · SHA-256: " + corto;
        return new Documento(
            tipo.name(), tipo.nombre(), s.informe().id(), s.informe().version(),
            np ? "No publicado" : "Publicado", FormatoFechas.fechaLarga(s.generadoEn()),
            fechaIso(s.proyecto().fechaInicio()), fechaIso(s.proyecto().fechaFin()),
            s.proyecto().nombre(), s.organizacion().nombre(), s.auditor().nombre(), np, huella, corto,
            s.plantillaVersion(), pie, "DOCUMENTO NO PUBLICADO", ResultadoValidacion.PENDIENTE,
            s.metricas().totalAreas(), s.metricas().aceptados());
    }

    private static String fechaIso(String iso) {
        return iso == null ? RAYA : FormatoFechas.fechaLarga(java.time.LocalDate.parse(iso));
    }

    private List<FilaHistorial> historial(InformeSnapshot s) {
        List<FilaHistorial> filas = new ArrayList<>();
        for (InformeSnapshot.EntradaHistorial h : s.historial()) {
            filas.add(new FilaHistorial(h.version(), etiquetaEstado(h.estado()),
                h.publicadoEn() == null ? RAYA : FormatoFechas.fechaLarga(h.publicadoEn()),
                h.despublicadoEn() == null ? RAYA : FormatoFechas.fechaLarga(h.despublicadoEn()),
                h.motivo() == null || h.motivo().isBlank() ? RAYA : h.motivo()));
        }
        return filas;
    }

    private static String etiquetaEstado(String estado) {
        return switch (estado) {
            case "BORRADOR" -> "Borrador";
            case "PUBLICADO" -> "Publicado";
            case "DESPUBLICADO" -> "Despublicado";
            default -> estado;
        };
    }

    // ---------------------------------------------------------------- narrativa y textos

    private Seccion aviso(String markdownFijo) {
        return new Seccion(null, markdown.analizar(markdownFijo).html(), true, false);
    }

    private Map<String, Seccion> secciones(TipoInforme tipo, Map<String, String> narrativa, boolean np) {
        Map<String, Seccion> resultado = new LinkedHashMap<>();
        for (TipoInforme.SeccionNarrativa seccion : tipo.secciones()) {
            var analizada = markdown.analizar(narrativa.get(seccion.clave()));
            boolean presente = !analizada.estaVacia();
            resultado.put(seccion.clave(),
                new Seccion(seccion.etiqueta(), analizada.html(), presente, !presente && seccion.obligatoria() && np));
        }
        return resultado;
    }

    private List<EntradaIndice> indice(InformeSnapshot s, boolean hayAnexo, boolean np) {
        List<EntradaIndice> entradas = new ArrayList<>();
        entradas.add(new EntradaIndice("Alcance y metodología", "sec-alcance", 1));
        entradas.add(new EntradaIndice("Escala de calificación", "sec-escala", 1));
        entradas.add(new EntradaIndice("Resumen de hallazgos", "sec-resumen", 1));
        entradas.add(new EntradaIndice("Hallazgos por área", "sec-hallazgos", 1));
        for (int i = 0; i < s.areas().size(); i++) {
            entradas.add(new EntradaIndice(s.areas().get(i).nombre(), "area-" + (i + 1), 2));
        }
        String conclusiones = s.narrativa().get("conclusiones");
        if (conclusiones != null && !conclusiones.isBlank()) {
            entradas.add(new EntradaIndice("Conclusiones", "sec-conclusiones", 1));
        }
        if (hayAnexo) {
            entradas.add(new EntradaIndice("Anexo A. Riesgos aceptados", "sec-anexo-a", 1));
        }
        entradas.add(new EntradaIndice("Anexo B. Glosario", "sec-anexo-b", 1));
        entradas.add(new EntradaIndice("Referencias", "sec-referencias", 1));
        return entradas;
    }

    private List<Referencia> referencias(List<String> referencias) {
        List<Referencia> resultado = new ArrayList<>();
        for (String referencia : referencias) {
            List<SegmentoTexto> segmentos = new ArrayList<>();
            String[] partes = referencia.split("\\*", -1);
            for (int i = 0; i < partes.length; i++) {
                if (!partes[i].isEmpty()) {
                    segmentos.add(new SegmentoTexto(partes[i], i % 2 == 1));
                }
            }
            resultado.add(new Referencia(segmentos));
        }
        return resultado;
    }

    // ---------------------------------------------------------------- severidad y riesgo

    static Celda severidad(String nombre) {
        Severidad severidad = Severidad.valueOf(nombre);
        return new Celda(severidad.etiqueta(), "sev-" + nombre.toLowerCase());
    }

    static Celda riesgo(InformeSnapshot.Riesgo riesgo) {
        if (riesgo == null) {
            return new Celda("Sin dato", "sev-sin");
        }
        NivelRiesgo nivel = NivelRiesgo.valueOf(riesgo.nivel());
        return new Celda(nivel.etiqueta(), "niv-" + nivel.clave());
    }

    private static String probabilidad(Integer p) {
        return p == null ? RAYA : p + " · " + EscalaRiesgo.etiquetaProbabilidad(p);
    }

    private static String impacto(int i) {
        return i + " · " + IMPACTO[i - 1];
    }

    private static List<Severidad> severidadesDeMayorAMenor() {
        List<Severidad> lista = new ArrayList<>(List.of(Severidad.values()));
        lista.sort((a, b) -> Integer.compare(a.posicionDeGravedad(), b.posicionDeGravedad()));
        return lista;
    }

    private static List<String> etiquetasSeveridad() {
        return severidadesDeMayorAMenor().stream().map(Severidad::etiqueta).toList();
    }

    private List<FilaMatrizNivel> matrizNiveles() {
        List<FilaMatrizNivel> filas = new ArrayList<>();
        for (int p = EscalaRiesgo.MAXIMO; p >= EscalaRiesgo.MINIMO; p--) {
            List<Celda> celdas = new ArrayList<>();
            for (int i = EscalaRiesgo.MINIMO; i <= EscalaRiesgo.MAXIMO; i++) {
                NivelRiesgo nivel = EscalaRiesgo.nivel(p, i);
                celdas.add(new Celda(nivel.etiqueta(), "niv-" + nivel.clave()));
            }
            filas.add(new FilaMatrizNivel(p + " · " + EscalaRiesgo.etiquetaProbabilidad(p), celdas));
        }
        return filas;
    }

    private static String porcentaje(int parte, int total) {
        return total == 0 ? "0 %" : Math.round(100.0 * parte / total) + " %";
    }

    private List<FilaConteo> conteoSeveridad(InformeSnapshot s) {
        int total = s.metricas().totalHallazgos();
        List<FilaConteo> filas = new ArrayList<>();
        for (Severidad severidad : severidadesDeMayorAMenor()) {
            int cantidad = s.metricas().porSeveridad().getOrDefault(severidad.name(), 0);
            int ancho = total == 0 ? 0 : (int) Math.round(100.0 * cantidad / total);
            filas.add(new FilaConteo(severidad.etiqueta(), "sev-" + severidad.name().toLowerCase(), cantidad,
                porcentaje(cantidad, total), ancho));
        }
        return filas;
    }

    private List<FilaArea> areaSeveridad(InformeSnapshot s) {
        int maximo = s.metricas().porAreaSeveridad().stream()
            .mapToInt(fila -> fila.porSeveridad().values().stream().mapToInt(Integer::intValue).sum()).max().orElse(0);
        List<FilaArea> filas = new ArrayList<>();
        for (InformeSnapshot.FilaAreaSeveridad fila : s.metricas().porAreaSeveridad()) {
            List<Integer> conteos = new ArrayList<>();
            List<Segmento> segmentos = new ArrayList<>();
            int total = 0;
            for (Severidad severidad : severidadesDeMayorAMenor()) {
                int cantidad = fila.porSeveridad().getOrDefault(severidad.name(), 0);
                conteos.add(cantidad);
                total += cantidad;
                if (cantidad > 0 && maximo > 0) {
                    segmentos.add(new Segmento("sev-" + severidad.name().toLowerCase(),
                        (int) Math.floor(100.0 * cantidad / maximo), severidad.etiqueta()));
                }
            }
            filas.add(new FilaArea(fila.area(), conteos, total, segmentos));
        }
        return filas;
    }

    private List<FilaRiesgoPrincipal> riesgosPrincipales(InformeSnapshot s, boolean np) {
        List<FilaRiesgoPrincipal> filas = new ArrayList<>();
        int posicion = 1;
        for (HallazgoDeArea par : OrdenHallazgos.topEjecutivo(s)) {
            Hallazgo h = par.hallazgo();
            filas.add(new FilaRiesgoPrincipal(posicion++, h.titulo(), par.area(), severidad(h.severidad()),
                riesgo(h.riesgo()), dimension(h)));
        }
        return filas;
    }

    /** Frase de plantilla por cada dimensión CIA más alta (sin jerga técnica). */
    private String dimension(Hallazgo h) {
        int maximo = Math.max(h.cia().c(), Math.max(h.cia().i(), h.cia().a()));
        List<String> frases = new ArrayList<>();
        if (h.cia().c() == maximo) {
            frases.add("Confidencialidad: " + textos.aviso("cia-c"));
        }
        if (h.cia().i() == maximo) {
            frases.add("Integridad: " + textos.aviso("cia-i"));
        }
        if (h.cia().a() == maximo) {
            frases.add("Disponibilidad: " + textos.aviso("cia-a"));
        }
        return String.join(" ", frases);
    }

    private List<FilaResumen> resumen(List<HallazgoDeArea> todos) {
        return todos.stream().map(par -> new FilaResumen(par.hallazgo().codigo(), par.hallazgo().titulo(), par.area(),
            severidad(par.hallazgo().severidad()), probabilidad(par.hallazgo().probabilidad()),
            riesgo(par.hallazgo().riesgo()), par.hallazgo().estado())).toList();
    }

    private List<AreaFichas> fichas(InformeSnapshot s, boolean np) {
        List<AreaFichas> areas = new ArrayList<>();
        int n = 1;
        for (Area area : s.areas()) {
            areas.add(new AreaFichas(area.nombre(), area.descripcion(), "area-" + n++,
                area.hallazgos().stream().map(h -> ficha(h, np)).toList()));
        }
        return areas;
    }

    private Ficha ficha(Hallazgo h, boolean np) {
        return new Ficha(h.codigo(), h.titulo(), severidad(h.severidad()),
            "C " + h.cia().c() + " · I " + h.cia().i() + " · A " + h.cia().a(), probabilidad(h.probabilidad()),
            impacto(h.impacto15()), riesgo(h.riesgo()),
            h.cves().stream().map(c -> new CveVista(c.id(), c.cvss() == null ? RAYA : c.cvss().toPlainString())).toList(),
            texto(h.mitigacion(), np), h.estado(), h.referencias(), Severidad.valueOf(h.severidad()).faseSugerida().descripcion());
    }

    private static String texto(String valor, boolean np) {
        if (valor != null && !valor.isBlank()) {
            return valor;
        }
        return np ? ResultadoValidacion.PENDIENTE : RAYA;
    }

    private List<FilaMitigacion> mitigacion(InformeSnapshot s, boolean np) {
        List<FilaMitigacion> filas = new ArrayList<>();
        int prioridad = 1;
        for (HallazgoDeArea par : OrdenHallazgos.paraMitigacion(s)) {
            Hallazgo h = par.hallazgo();
            filas.add(new FilaMitigacion(prioridad++, h.codigo(), h.titulo(), par.area(), severidad(h.severidad()),
                Severidad.valueOf(h.severidad()).faseSugerida().descripcion(), texto(h.mitigacion(), np), h.estado()));
        }
        return filas;
    }

    private List<FilaMatriz> registroMatriz(InformeSnapshot s, boolean np) {
        List<FilaMatriz> filas = new ArrayList<>();
        for (HallazgoDeArea par : OrdenHallazgos.paraMatrizRiesgo(s)) {
            Hallazgo h = par.hallazgo();
            Celda riesgo = riesgo(h.riesgo());
            var cve = h.cves().isEmpty() ? null : h.cves().get(0);
            filas.add(new FilaMatriz(h.codigo(), par.area(), h.titulo(), Integer.toString(h.cia().c()),
                Integer.toString(h.cia().i()), Integer.toString(h.cia().a()), severidad(h.severidad()),
                probabilidad(h.probabilidad()), impacto(h.impacto15()),
                h.riesgo() == null ? RAYA : Integer.toString(h.riesgo().puntaje()), riesgo, riesgo.clase(),
                cve == null ? RAYA : cve.id(), cve == null || cve.cvss() == null ? RAYA : cve.cvss().toPlainString(),
                h.estado(), texto(h.mitigacion(), np)));
        }
        return filas;
    }

    private List<FilaCalor> mapaCalor(InformeSnapshot s) {
        List<FilaCalor> filas = new ArrayList<>();
        for (int p = EscalaRiesgo.MAXIMO; p >= EscalaRiesgo.MINIMO; p--) {
            List<CeldaCalor> celdas = new ArrayList<>();
            for (int i = EscalaRiesgo.MINIMO; i <= EscalaRiesgo.MAXIMO; i++) {
                int valor = s.metricas().mapaCalor().get(p - 1).get(i - 1);
                celdas.add(new CeldaCalor(valor, "niv-" + EscalaRiesgo.nivel(p, i).clave()));
            }
            filas.add(new FilaCalor(p + " · " + EscalaRiesgo.etiquetaProbabilidad(p), celdas));
        }
        return filas;
    }

    private List<FilaNivel> conteoNiveles(List<HallazgoDeArea> todos) {
        List<FilaNivel> filas = new ArrayList<>();
        for (NivelRiesgo nivel : NivelRiesgo.deMayorAMenor()) {
            long cantidad = todos.stream().filter(par -> par.hallazgo().riesgo() != null
                && par.hallazgo().riesgo().nivel().equals(nivel.name())).count();
            filas.add(new FilaNivel(nivel.etiqueta(), "niv-" + nivel.clave(), (int) cantidad));
        }
        return filas;
    }

    private FilaAnexoA filaAnexoA(EntradaAnexoA e) {
        return new FilaAnexoA(e.codigo(), e.titulo(), e.area(), severidad(e.severidad()),
            EntradaAnexoA.FALSO_POSITIVO.equals(e.tipo()) ? "Falso positivo" : "Riesgo aceptado");
    }
}
