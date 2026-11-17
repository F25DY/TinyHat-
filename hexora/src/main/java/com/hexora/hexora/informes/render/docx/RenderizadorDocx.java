package com.hexora.hexora.informes.render.docx;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.markdown.BloqueNarrativa;
import com.hexora.hexora.informes.markdown.MarkdownSaneado;
import com.hexora.hexora.informes.render.InformeRenderer;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextosInforme;
import com.hexora.hexora.informes.render.docx.ConstructorDocx.Cd;
import com.hexora.hexora.informes.render.pdf.ConstructorVistaPdf;
import com.hexora.hexora.informes.render.pdf.VistaPdf;
import com.hexora.hexora.informes.render.pdf.VistaPdf.Ficha;
import com.hexora.hexora.informes.render.pdf.VistaPdf.Referencia;
import com.hexora.hexora.informes.render.pdf.VistaPdf.SegmentoTexto;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

/**
 * Renderizador DOCX (Técnico, Ejecutivo, Mitigación y Final de entrega). Parte de {@code informes/docx/base.docx} y
 * construye el contenido por código a partir de la misma vista que usa el PDF, de modo que cifras, orden y colores
 * son idénticos. Sin hipervínculos, relaciones externas, objetos incrustados, macros ni índice automático (§7.2).
 * La versión de referencia del informe es el PDF (§6.4).
 */
@Component
public class RenderizadorDocx implements InformeRenderer {

    private static final String LOGO = "informes/pdf/img/logo-tinyhat.png";
    private static final int[] ANCHOS_CALOR = {2000, 1472, 1472, 1472, 1472, 1472};

    private final ConstructorVistaPdf vistas;
    private final MarkdownSaneado markdown;
    private final TextosInforme textos;

    public RenderizadorDocx(ConstructorVistaPdf vistas, MarkdownSaneado markdown, TextosInforme textos) {
        this.vistas = vistas;
        this.markdown = markdown;
        this.textos = textos;
    }

    @Override
    public FormatoInforme formato() {
        return FormatoInforme.DOCX;
    }

    @Override
    public boolean soporta(TipoInforme tipo) {
        return tipo.admite(FormatoInforme.DOCX);
    }

    @Override
    public void render(InformeSnapshot snapshot, OpcionesRender opciones, OutputStream salida) throws IOException {
        TipoInforme tipo = TipoInforme.valueOf(snapshot.informe().tipo());
        tipo.requerirFormato(FormatoInforme.DOCX);
        VistaPdf v = vistas.construir(snapshot, opciones);
        boolean horizontal = tipo == TipoInforme.MIT;
        try (ConstructorDocx d = ConstructorDocx.desdeBase()) {
            d.pagina(horizontal);
            d.encabezadoYPie("TinyHat · " + v.doc().tipoNombre() + " · " + v.doc().proyecto(), v.doc().noPublicado(),
                v.doc().pie());
            d.propiedades(v.doc().tipoNombre() + " · " + v.doc().proyecto(), v.doc().id());
            Contexto c = new Contexto(d, v, snapshot);
            c.portada();
            c.control();
            c.avisos();
            switch (tipo) {
                case TEC -> c.tecnico();
                case EJE -> c.ejecutivo();
                case MIT -> c.mitigacion();
                case FIN -> c.finalEntrega();
                default -> throw new IllegalArgumentException("No hay renderizador DOCX para ese tipo");
            }
            d.documento().write(salida);
        }
    }

    /** Estado de un renderizado: el constructor, la vista y el snapshot. */
    private final class Contexto {

        private final ConstructorDocx d;
        private final VistaPdf v;
        private final InformeSnapshot s;

        Contexto(ConstructorDocx d, VistaPdf v, InformeSnapshot s) {
            this.d = d;
            this.v = v;
            this.s = s;
        }

        // ------------------------------------------------------------ elementos comunes

        void portada() throws IOException {
            try (InputStream logo = getClass().getClassLoader().getResourceAsStream(LOGO)) {
                if (logo != null) {
                    d.logo(logo, 72);
                }
            }
            d.parrafoCentrado("PortadaTipo", v.doc().tipoNombre());
            d.parrafoCentrado("PortadaProyecto", v.doc().proyecto());
            d.parrafoCentrado("PortadaOrganizacion", v.doc().organizacion());
            d.tabla(new int[] {2600, 4760}, null, List.of(
                List.of(Cd.negrita("Identificador"), Cd.mono(v.doc().id())),
                List.of(Cd.negrita("Versión"), Cd.t(Integer.toString(v.doc().version()))),
                List.of(Cd.negrita("Estado"), Cd.t(v.doc().estado())),
                List.of(Cd.negrita("Fecha de emisión"), Cd.t(v.doc().fechaEmision())),
                List.of(Cd.negrita("Auditor"), Cd.t(v.doc().auditor())))).setTableAlignment(TableRowAlign.CENTER);
            d.parrafoCentrado("Confidencial", "CONFIDENCIAL");
            if (v.avisoNoPublicado() != null) {
                d.narrativa(markdown.analizar(textos.aviso("no-publicado")).bloques(), "AvisoNP");
            }
        }

        void control() {
            d.titulo(1, "Control del documento", true);
            d.titulo(2, "Historial de versiones", false);
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaHistorial h : v.historial()) {
                filas.add(List.of(Cd.t(Integer.toString(h.version())), Cd.t(h.estado()), Cd.t(h.publicado()),
                    Cd.t(h.despublicado()), Cd.t(h.motivo())));
            }
            d.tabla(new int[] {900, 1500, 2000, 2000, 2960},
                List.of(Cd.t("Versión"), Cd.t("Estado"), Cd.t("Publicada"), Cd.t("Despublicada"),
                    Cd.t("Motivo de despublicación")), filas);
            d.nota("Se listan las versiones de este tipo de informe en el proyecto hasta la presente.");
            d.titulo(2, "Datos de control", false);
            d.tabla(new int[] {2600, 6760}, null, List.of(
                List.of(Cd.negrita("Identificador"), Cd.mono(v.doc().id())),
                List.of(Cd.negrita("SHA-256 del contenido"), Cd.mono(v.doc().hashCompleto())),
                List.of(Cd.negrita("Versión de la plantilla"), Cd.t(v.doc().plantillaVersion())),
                List.of(Cd.negrita("Periodo del proyecto"), Cd.t(v.doc().fechaInicio() + " al " + v.doc().fechaFin()))));
            d.nota("El SHA-256 se calcula sobre el contenido congelado del informe y permite comprobar que no fue alterado.");
        }

        void avisos() {
            d.narrativa(markdown.analizar(textos.aviso("referencia")).bloques(), "Aviso");
            d.narrativa(markdown.analizar(textos.aviso("alcance")).bloques(), "Aviso");
            if (v.avisoNoPublicado() != null) {
                d.narrativa(markdown.analizar(textos.aviso("no-publicado")).bloques(), "AvisoNP");
            }
            recuadroAnexoA();
        }

        void recuadroAnexoA() {
            if (v.avisoAnexoA() != null) {
                d.narrativa(markdown.analizar(
                    textos.avisoAnexoA(s.metricas().aceptados(), s.metricas().falsosPositivos())).bloques(), "Aviso");
            }
        }

        /** Texto de una sección de la narrativa; si falta y es obligatoria en una copia NP, deja el aviso de pendiente. */
        void seccion(String clave) {
            VistaPdf.Seccion seccion = v.secciones().get(clave);
            if (seccion == null) {
                return;
            }
            if (seccion.presente()) {
                d.narrativa(markdown.analizar(s.narrativa().get(clave)).bloques(), null);
            } else if (seccion.pendiente()) {
                d.parrafo("AvisoNP", v.doc().pendiente());
            }
        }

        boolean presente(String clave) {
            VistaPdf.Seccion seccion = v.secciones().get(clave);
            return seccion != null && seccion.presente();
        }

        void bloques(List<BloqueNarrativa> bloques, String estilo) {
            d.narrativa(bloques, estilo);
        }

        // ------------------------------------------------------------ tablas compartidas

        void tablaConteoSeveridad() {
            d.leyendaTabla("Hallazgos por severidad");
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaConteo f : v.conteoSeveridad()) {
                filas.add(List.of(Cd.paleta(f.etiqueta(), f.clase()), Cd.centro(Integer.toString(f.cantidad())),
                    Cd.centro(f.porcentaje())));
            }
            filas.add(List.of(Cd.negrita("Total"), new Cd(Integer.toString(v.totalHallazgos()), null, null, true, false, true),
                Cd.centro("100 %")));
            d.tabla(new int[] {3000, 2000, 2000}, List.of(Cd.t("Severidad"), Cd.centro("Hallazgos"), Cd.centro("Proporción")),
                filas);
            d.nota("Solo cuenta los hallazgos del cuerpo del informe; los de riesgo aceptado van en el Anexo A.");
        }

        void tablaPorArea() {
            d.leyendaTabla("Hallazgos por área y severidad");
            List<Cd> encabezado = new ArrayList<>();
            encabezado.add(Cd.t("Área"));
            v.etiquetasSeveridad().forEach(e -> encabezado.add(Cd.centro(e)));
            encabezado.add(Cd.centro("Total"));
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaArea a : v.areaSeveridad()) {
                List<Cd> fila = new ArrayList<>();
                fila.add(Cd.t(a.area()));
                a.conteos().forEach(n -> fila.add(Cd.centro(Integer.toString(n))));
                fila.add(Cd.centro(Integer.toString(a.total())));
                filas.add(fila);
            }
            d.tabla(new int[] {2300, 1100, 1100, 1100, 1100, 1100, 1560}, encabezado, filas);
            d.nota("Cada fila cuenta los hallazgos del área por severidad.");
        }

        void tablaNiveles() {
            d.leyendaTabla("Hallazgos por nivel de riesgo");
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaNivel n : v.conteoNiveles()) {
                filas.add(List.of(Cd.paleta(n.etiqueta(), n.clase()), Cd.centro(Integer.toString(n.cantidad()))));
            }
            d.tabla(new int[] {3000, 2000}, List.of(Cd.t("Nivel de riesgo"), Cd.centro("Hallazgos")), filas);
        }

        void mapaCalor() {
            d.leyendaFigura("Mapa de calor de riesgo: probabilidad por impacto");
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaCalor f : v.mapaCalor()) {
                List<Cd> fila = new ArrayList<>();
                fila.add(Cd.negrita(f.probabilidad()));
                f.celdas().forEach(c -> fila.add(Cd.paleta(Integer.toString(c.valor()), c.clase())));
                filas.add(fila);
            }
            d.tabla(ANCHOS_CALOR, encabezadoMatriz(), filas);
            d.nota("Cada celda indica cuántos hallazgos tienen esa probabilidad e impacto; el color es el nivel de riesgo "
                + "de la matriz de NIST SP 800-30."
                + (v.sinProbabilidad() > 0 ? " Hallazgos sin probabilidad: " + v.sinProbabilidad() + "." : ""));
        }

        void matrizNiveles() {
            d.leyendaTabla("Matriz de niveles de riesgo (NIST SP 800-30 Rev. 1, Tabla I-2)");
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaMatrizNivel f : v.matrizNiveles()) {
                List<Cd> fila = new ArrayList<>();
                fila.add(Cd.negrita(f.probabilidad()));
                f.celdas().forEach(c -> fila.add(Cd.paleta(c.texto(), c.clase())));
                filas.add(fila);
            }
            d.tabla(ANCHOS_CALOR, encabezadoMatriz(), filas);
            d.nota("El nivel de riesgo sale de esta tabla de búsqueda, no de umbrales sobre el puntaje.");
        }

        private List<Cd> encabezadoMatriz() {
            List<Cd> encabezado = new ArrayList<>();
            encabezado.add(Cd.t("Probabilidad \\ Impacto"));
            for (int i = 0; i < v.etiquetasImpacto().size(); i++) {
                encabezado.add(Cd.centro((i + 1) + " · " + v.etiquetasImpacto().get(i)));
            }
            return encabezado;
        }

        void tablaResumen() {
            if (v.resumenHallazgos().isEmpty()) {
                d.parrafo(null, "El informe no incluye hallazgos en el cuerpo.");
                return;
            }
            d.leyendaTabla("Resumen de hallazgos");
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaResumen r : v.resumenHallazgos()) {
                filas.add(List.of(Cd.mono(r.codigo()), Cd.t(r.titulo()), Cd.t(r.area()),
                    Cd.paleta(r.severidad().texto(), r.severidad().clase()), Cd.t(r.probabilidad()),
                    Cd.paleta(r.riesgo().texto(), r.riesgo().clase()), Cd.t(r.estado())));
            }
            d.tabla(new int[] {1100, 2300, 1400, 1000, 1300, 1000, 1260},
                List.of(Cd.t("Código"), Cd.t("Hallazgo"), Cd.t("Área"), Cd.centro("Severidad"), Cd.t("Probabilidad"),
                    Cd.centro("Riesgo"), Cd.t("Estado")), filas);
        }

        void tablaMitigacion(int[] anchos, boolean conActivo) {
            if (v.mitigacion().isEmpty()) {
                d.parrafo(null, "El informe no incluye hallazgos en el cuerpo.");
                return;
            }
            d.leyendaTabla("Plan de mitigación ordenado por severidad");
            List<Cd> encabezado = new ArrayList<>(List.of(Cd.centro("Prioridad"), Cd.t("Código"), Cd.t("Hallazgo"),
                Cd.t("Área"), Cd.centro("Severidad"), Cd.t("Fase sugerida"), Cd.t("Acción de mitigación"),
                Cd.t("Estado")));
            if (conActivo) {
                encabezado.add(Cd.t("Activo"));
            }
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaMitigacion m : v.mitigacion()) {
                List<Cd> fila = new ArrayList<>(List.of(Cd.centro(Integer.toString(m.prioridad())), Cd.mono(m.codigo()),
                    Cd.t(m.titulo()), Cd.t(m.area()), Cd.paleta(m.severidad().texto(), m.severidad().clase()),
                    Cd.t(m.fase()), Cd.t(m.accion()), Cd.t(m.estado())));
                if (conActivo) {
                    fila.add(Cd.vacia());
                }
                filas.add(fila);
            }
            d.tabla(anchos, encabezado, filas);
            d.narrativa(markdown.analizar(textos.aviso("fase-sugerida")).bloques(), "Nota");
        }

        void anexoA() {
            if (v.anexoA().isEmpty()) {
                return;
            }
            d.titulo(1, "Anexo A. Riesgos aceptados", true);
            d.leyendaTabla("Hallazgos con riesgo aceptado");
            List<List<Cd>> filas = new ArrayList<>();
            for (VistaPdf.FilaAnexoA a : v.anexoA()) {
                filas.add(List.of(Cd.mono(a.codigo()), Cd.t(a.titulo()), Cd.t(a.area()),
                    Cd.paleta(a.severidad().texto(), a.severidad().clase()), Cd.t(a.tipo())));
            }
            d.tabla(new int[] {1200, 3200, 2000, 1300, 1660},
                List.of(Cd.t("Código"), Cd.t("Hallazgo"), Cd.t("Área"), Cd.centro("Severidad"), Cd.t("Tipo")), filas);
            d.nota("Estos hallazgos no se incluyen en las métricas principales. La aplicación no registra una "
                + "justificación para el riesgo aceptado.");
        }

        void referencias() {
            if (v.referencias().isEmpty()) {
                return;
            }
            d.titulo(1, "Referencias", true);
            for (Referencia r : v.referencias()) {
                XWPFParagraph p = d.documento().createParagraph();
                p.setStyle("Referencia");
                for (SegmentoTexto segmento : r.segmentos()) {
                    XWPFRun run = p.createRun();
                    run.setItalic(segmento.cursiva());
                    run.setText(com.hexora.hexora.informes.render.TextoSeguro.limpiarXml(segmento.texto()));
                }
            }
        }

        void ficha(Ficha f) {
            XWPFParagraph p = d.parrafo("Ficha", f.codigo() + "  " + f.titulo());
            p.setSpacingBefore(240);
            List<List<Cd>> filas = new ArrayList<>();
            filas.add(List.of(Cd.negrita("Severidad"), Cd.paleta(f.severidad().texto(), f.severidad().clase())));
            filas.add(List.of(Cd.negrita("Confidencialidad, integridad, disponibilidad"), Cd.t(f.cia())));
            filas.add(List.of(Cd.negrita("Probabilidad"), Cd.t(f.probabilidad())));
            filas.add(List.of(Cd.negrita("Impacto"), Cd.t(f.impacto())));
            filas.add(List.of(Cd.negrita("Riesgo"), Cd.paleta(f.riesgo().texto(), f.riesgo().clase())));
            if (!f.cves().isEmpty()) {
                StringBuilder cves = new StringBuilder();
                for (VistaPdf.CveVista c : f.cves()) {
                    cves.append(c.id()).append("  CVSS ").append(c.cvss()).append('\n');
                }
                filas.add(List.of(Cd.negrita("CVE y CVSS"), Cd.mono(cves.toString().stripTrailing())));
            }
            filas.add(List.of(Cd.negrita("Acción de mitigación"), Cd.t(f.mitigacion())));
            filas.add(List.of(Cd.negrita("Estado"), Cd.t(f.estado())));
            filas.add(List.of(Cd.negrita("Fase sugerida"), Cd.t(f.fase())));
            if (!f.referencias().isEmpty()) {
                filas.add(List.of(Cd.negrita("Referencias"), Cd.mono(String.join("\n", f.referencias()))));
            }
            d.tabla(new int[] {2400, 6960}, null, filas);
        }

        // ------------------------------------------------------------ tipos

        void tecnico() {
            d.titulo(1, v.secciones().get("alcanceMetodologia").titulo(), true);
            seccion("alcanceMetodologia");
            d.titulo(1, "Escala de calificación", false);
            bloques(markdown.analizar(textos.escalaCalificacion()).bloques(), null);
            matrizNiveles();
            d.titulo(1, "Resumen de hallazgos", true);
            tablaResumen();
            if (!v.resumenHallazgos().isEmpty()) {
                d.nota("Las fichas completas están en la sección siguiente, agrupadas por área.");
            }
            d.titulo(1, "Hallazgos por área", true);
            if (v.areasFichas().isEmpty()) {
                d.parrafo(null, "El proyecto no tiene áreas registradas.");
            }
            for (VistaPdf.AreaFichas area : v.areasFichas()) {
                d.titulo(2, area.nombre(), false);
                if (area.descripcion() != null) {
                    d.parrafo(null, area.descripcion());
                }
                if (area.fichas().isEmpty()) {
                    d.parrafo(null, "Sin hallazgos en el cuerpo del informe.");
                }
                area.fichas().forEach(this::ficha);
            }
            if (presente("conclusiones")) {
                d.titulo(1, v.secciones().get("conclusiones").titulo(), true);
                seccion("conclusiones");
            }
            anexoA();
            d.titulo(1, "Anexo B. Glosario", true);
            bloques(markdown.analizar(textos.glosario()).bloques(), null);
            referencias();
        }

        void ejecutivo() {
            d.titulo(1, v.secciones().get("resumenEjecutivo").titulo(), true);
            seccion("resumenEjecutivo");
            d.titulo(1, "Estado general", false);
            d.parrafo(null, "El proyecto incluye " + v.doc().totalAreas() + " área(s) y " + v.totalHallazgos()
                + " hallazgo(s) en el cuerpo del informe.");
            tablaConteoSeveridad();
            tablaPorArea();
            d.titulo(1, "Los riesgos principales", false);
            if (v.riesgosPrincipales().isEmpty()) {
                d.parrafo(null, "El informe no incluye hallazgos en el cuerpo.");
            } else {
                d.leyendaTabla("Riesgos principales");
                List<List<Cd>> filas = new ArrayList<>();
                for (VistaPdf.FilaRiesgoPrincipal r : v.riesgosPrincipales()) {
                    filas.add(List.of(Cd.centro(Integer.toString(r.posicion())), Cd.t(r.titulo()), Cd.t(r.area()),
                        Cd.paleta(r.severidad().texto(), r.severidad().clase()),
                        Cd.paleta(r.riesgo().texto(), r.riesgo().clase()), Cd.t(r.dimension())));
                }
                d.tabla(new int[] {600, 2400, 1500, 1100, 1100, 2660},
                    List.of(Cd.centro("N.°"), Cd.t("Hallazgo"), Cd.t("Área"), Cd.centro("Severidad"), Cd.centro("Riesgo"),
                        Cd.t("Qué podría ocurrir")), filas);
                d.nota("Se muestran hasta cinco hallazgos, ordenados por severidad y nivel de riesgo.");
            }
            if (presente("recomendaciones")) {
                d.titulo(1, v.secciones().get("recomendaciones").titulo(), false);
                seccion("recomendaciones");
            }
        }

        void mitigacion() {
            if (presente("introduccion")) {
                d.titulo(1, v.secciones().get("introduccion").titulo(), true);
                seccion("introduccion");
            }
            d.titulo(1, "Plan de mitigación", !presente("introduccion"));
            tablaMitigacion(new int[] {700, 1150, 2200, 1400, 1050, 1500, 2700, 1000, 1260}, true);
            d.nota("Las fases de remediación se derivan de la severidad. La columna Activo queda en blanco para que la "
                + "complete el cliente.");
        }

        void finalEntrega() {
            d.titulo(1, "Parte I. Informe ejecutivo", true);
            d.titulo(2, v.secciones().get("cartaEntrega").titulo(), false);
            seccion("cartaEntrega");
            d.titulo(2, v.secciones().get("resumenEjecutivo").titulo(), false);
            seccion("resumenEjecutivo");
            d.titulo(2, "Estado del riesgo", false);
            d.parrafo(null, "El proyecto incluye " + v.doc().totalAreas() + " área(s) y " + v.totalHallazgos()
                + " hallazgo(s) en el cuerpo del informe.");
            mapaCalor();
            tablaNiveles();
            tablaConteoSeveridad();

            d.titulo(1, "Parte II. Informe técnico", true);
            d.titulo(2, v.secciones().get("alcanceMetodologia").titulo(), false);
            seccion("alcanceMetodologia");
            d.titulo(2, "Resultados", false);
            tablaResumen();
            if (!v.resumenHallazgos().isEmpty()) {
                d.nota("El detalle de cada hallazgo está en el informe técnico publicado.");
            }
            d.titulo(2, "Plan de mitigación", false);
            tablaMitigacion(new int[] {600, 1000, 1900, 1200, 1000, 1100, 1760, 800}, false);

            d.titulo(1, v.secciones().get("conclusiones").titulo(), false);
            seccion("conclusiones");

            d.titulo(1, "Trazabilidad", false);
            if (v.trazabilidad().isEmpty()) {
                d.parrafo(null, "El proyecto no tiene otros informes publicados vigentes.");
            } else {
                d.leyendaTabla("Paquete de entregables");
                List<List<Cd>> filas = new ArrayList<>();
                for (VistaPdf.FilaTraza t : v.trazabilidad()) {
                    filas.add(List.of(Cd.mono(t.id()), Cd.t(t.tipo()), Cd.t(t.formato()), Cd.mono(t.sha256()),
                        Cd.t(t.publicado())));
                }
                d.tabla(new int[] {1900, 1500, 900, 3200, 1860},
                    List.of(Cd.t("Identificador"), Cd.t("Tipo"), Cd.t("Formato"), Cd.t("SHA-256"), Cd.t("Publicado")),
                    filas);
                d.nota("Informes publicados vigentes del proyecto y la huella de cada archivo. La versión de referencia es el PDF.");
            }

            d.titulo(1, "Constancia de entrega", true);
            d.parrafo(null, "Las partes dejan constancia de la entrega del presente informe y de los entregables del "
                + "proyecto " + v.doc().proyecto() + ".");
            firma("Auditor", "Firma del auditor");
            firma("Representante del cliente", "Firma del representante del cliente");

            d.titulo(2, "Lista de verificación de cierre", false);
            d.nota("Para marcar a mano al entregar:");
            for (String punto : List.of("Cuentas temporales eliminadas.", "Archivos, payloads y binarios retirados.",
                "Configuraciones revertidas.", "Servicio verificado sin degradación.")) {
                d.parrafo(null, "[   ] " + punto);
            }
            anexoA();
            referencias();
        }

        private void firma(String rol, String leyenda) {
            d.tabla(new int[] {3000, 6360}, null, List.of(
                List.of(Cd.negrita(rol), Cd.vacia()), List.of(Cd.negrita("Nombre y cargo"), Cd.vacia()),
                List.of(Cd.negrita("Fecha"), Cd.vacia())));
            XWPFParagraph p = d.parrafo("Nota", leyenda);
            p.setAlignment(ParagraphAlignment.LEFT);
        }
    }
}
