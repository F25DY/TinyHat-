package com.hexora.hexora.informes.render.pdf;

import static com.hexora.hexora.informes.render.pdf.MuestrasPdfTest.renderizar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.render.DatosDemo;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import com.hexora.hexora.informes.snapshot.SerializadorSnapshot;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** El renderizador PDF de los cinco tipos, con datos ficticios. */
class RenderizadorPdfTest {

    private static String normalizar(String texto) {
        return texto.replaceAll("\\s+", " ");
    }

    // ---------------------------------------------------------------- estructura común

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void cadaTipoSeGeneraPublicadoYNoPublicadoConPieEnTodasLasPaginas(TipoInforme tipo) throws Exception {
        for (boolean np : new boolean[] {false, true}) {
            InformeSnapshot snapshot = DatosDemo.snapshot(tipo, np);
            byte[] pdf = renderizar(snapshot, np);

            assertThat(PdfPrueba.paginas(pdf)).as(tipo + " np=" + np).isGreaterThanOrEqualTo(3);
            String hash12 = SerializadorSnapshot.huella(snapshot).substring(0, 12);
            for (int pagina = 1; pagina <= PdfPrueba.paginas(pdf); pagina++) {
                String texto = normalizar(PdfPrueba.textoPagina(pdf, pagina));
                assertThat(texto).as("pie de la página " + pagina)
                    .contains(snapshot.informe().id() + " · CONFIDENCIAL · SHA-256: " + hash12)
                    .contains(pagina + " de " + PdfPrueba.paginas(pdf));
            }
            String todo = normalizar(PdfPrueba.texto(pdf));
            assertThat(todo).contains("CONFIDENCIAL").contains(snapshot.proyecto().nombre())
                .contains("Comercializadora Andina S.A.S. (ficticia)").contains("Firma Auditora Demo S.A.S.")
                .contains("20 de noviembre de 2026").contains(SerializadorSnapshot.huella(snapshot));
        }
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void lasTildesYLaEnyeSeConservanYLasFuentesVanIncrustadas(TipoInforme tipo) throws Exception {
        byte[] pdf = renderizar(DatosDemo.snapshot(tipo, false), false);
        String texto = normalizar(PdfPrueba.texto(pdf));
        assertThat(texto).contains("Evaluación de seguridad de la plataforma de ventas").contains("Gestión de identidades");
        assertThat(PdfPrueba.fuentes(pdf)).isNotEmpty()
            .allMatch(nombre -> nombre.contains("PlusJakartaSans") || nombre.contains("JetBrainsMono"));
        if (tipo != TipoInforme.EJE) {
            assertThat(texto).contains("ñoquis");
        }
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void laOrientacionDeLaPaginaDependeDelTipo(TipoInforme tipo) throws Exception {
        byte[] pdf = renderizar(DatosDemo.snapshot(tipo, false), false);
        boolean horizontal = tipo == TipoInforme.MR || tipo == TipoInforme.MIT;
        for (int pagina = 0; pagina < PdfPrueba.paginas(pdf); pagina++) {
            float[] tamano = PdfPrueba.tamano(pdf, pagina);
            assertThat(tamano[0]).as(tipo + " página " + (pagina + 1) + " ancho (carta)")
                .isCloseTo(horizontal ? 792f : 612f, org.assertj.core.data.Offset.offset(1f));
            assertThat(tamano[1]).isCloseTo(horizontal ? 612f : 792f, org.assertj.core.data.Offset.offset(1f));
        }
    }

    // ---------------------------------------------------------------- copias no publicadas

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void unaCopiaNoPublicadaLlevaAvisoYMarcaYUnPublicadoNo(TipoInforme tipo) throws Exception {
        String publicado = normalizar(PdfPrueba.texto(renderizar(DatosDemo.snapshot(tipo, false), false)));
        assertThat(publicado).doesNotContain("DOCUMENTO NO PUBLICADO").doesNotContain("-NP");

        byte[] np = renderizar(DatosDemo.snapshot(tipo, true), true);
        assertThat(normalizar(PdfPrueba.texto(np)))
            .contains("DOCUMENTO NO PUBLICADO — copia de trabajo sin validez como entregable.")
            .contains("-NP");
        // La marca de agua es un elemento fijo: está en cada página.
        for (int pagina = 1; pagina <= PdfPrueba.paginas(np); pagina++) {
            assertThat(normalizar(PdfPrueba.textoPagina(np, pagina))).as("marca de agua en la página " + pagina)
                .contains("DOCUMENTO");
        }
    }

    @Test
    void enUnaCopiaNpLosFaltantesSeImprimenComoPendientes() throws Exception {
        InformeSnapshot sinNarrativa = DatosDemo.snapshot(TipoInforme.FIN, true, false);
        String texto = normalizar(PdfPrueba.texto(renderizar(sinNarrativa, true)));
        // FIN tiene cuatro secciones obligatorias vacías.
        assertThat(texto.split("\\[PENDIENTE DE COMPLETAR POR EL AUDITOR\\]", -1).length - 1).isEqualTo(4);

        InformeSnapshot sinResumen = DatosDemo.snapshot(TipoInforme.EJE, true, false);
        assertThat(normalizar(PdfPrueba.texto(renderizar(sinResumen, true))))
            .contains("[PENDIENTE DE COMPLETAR POR EL AUDITOR]");
    }

    // ---------------------------------------------------------------- contenido por tipo

    @Test
    void elEjecutivoNoLlevaJergaTecnicaNiCveYTieneDeTresACincoPaginas() throws Exception {
        byte[] pdf = renderizar(DatosDemo.snapshot(TipoInforme.EJE, false), false);
        String texto = normalizar(PdfPrueba.texto(pdf));
        assertThat(PdfPrueba.paginas(pdf)).isBetween(3, 5);
        assertThat(texto).contains("Resumen ejecutivo").contains("Estado general").contains("Los riesgos principales")
            .contains("Recomendaciones").contains("Tabla 1").contains("Tabla 2")
            .contains("Hallazgos por severidad").contains("Hallazgos por área y severidad")
            .contains("Confidencialidad: Podría quedar expuesta información que debe mantenerse reservada.")
            .contains("Importante: este proyecto incluye 2 hallazgo(s) con riesgo aceptado y 0 falso(s) positivo(s).");
        assertThat(texto).doesNotContain("CVE-2099").doesNotContain("CVSS").doesNotContain("H-A1B2C3");
        // Los cinco principales: la inyección SQL (Crítica) va primero.
        assertThat(texto.indexOf("Inyección SQL en el buscador"))
            .isLessThan(texto.indexOf("Servicio de administración remota"));
    }

    @Test
    void elTecnicoTieneIndiceFichasPorAreaAnexosYReferencias() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.TEC, false);
        byte[] pdf = renderizar(snapshot, false);
        String texto = normalizar(PdfPrueba.texto(pdf));

        assertThat(texto).contains("Índice").contains("Alcance y metodología").contains("Escala de calificación")
            .contains("Resumen de hallazgos").contains("Hallazgos por área").contains("Conclusiones")
            .contains("Anexo A. Riesgos aceptados").contains("Anexo B. Glosario").contains("Referencias")
            .contains("Matriz de niveles de riesgo (NIST SP 800-30 Rev. 1, Tabla I-2)")
            .contains("Fase sugerida: F1 — Contención inmediata (0–7 días)")
            .contains("CVE-2099-0001").contains("CVSS 9.8").contains("https://proveedor.ejemplo/avisos/2099-0001");
        // Una ficha por hallazgo del cuerpo y ninguna de los aceptados.
        for (var area : snapshot.areas()) {
            for (var hallazgo : area.hallazgos()) {
                assertThat(texto).as(hallazgo.codigo()).contains(hallazgo.codigo());
            }
        }
        assertThat(PdfPrueba.marcadores(pdf)).as("marcadores del PDF: " + PdfPrueba.marcadores(pdf)).isGreaterThan(3);
        // El índice lleva puntos guía y un número de página al final de cada entrada.
        assertThat(texto).containsPattern("Anexo B\\. Glosario \\.{3,} \\d+");
        // (La extracción de texto separa los tramos en cursiva; se comprueban las piezas.)
        assertThat(texto).contains("Forum of Incident Response and Security Teams. (2019).")
            .contains("National Institute of Standards and Technology. (2099).")
            .contains("CVE-2099-0001 detail").contains("https://nvd.nist.gov/vuln/detail/CVE-2099-0001");
    }

    @Test
    void laMitigacionOrdenaPorSeveridadYDejaLaColumnaActivoVacia() throws Exception {
        byte[] pdf = renderizar(DatosDemo.snapshot(TipoInforme.MIT, false), false);
        String texto = normalizar(PdfPrueba.texto(pdf));
        assertThat(texto).contains("Plan de mitigación ordenado por severidad").contains("Prioridad").contains("Activo")
            .contains("Fase sugerida").contains("Acción de mitigación")
            .contains("Las fases de remediación son una sugerencia derivada de la severidad");
        int critica = texto.indexOf("Inyección SQL en el buscador");
        int alta = texto.indexOf("Servicio de administración remota");
        int baja = texto.indexOf("Falta de cabeceras de seguridad");
        assertThat(critica).isLessThan(alta);
        assertThat(alta).isLessThan(baja);
    }

    @Test
    void laMatrizTieneResumenMapaDeCalorRegistroColoreadoYElAvisoDeOrden() throws Exception {
        byte[] pdf = renderizar(DatosDemo.snapshot(TipoInforme.MR, false), false);
        String texto = normalizar(PdfPrueba.texto(pdf));
        assertThat(texto).contains("Resumen").contains("Hallazgos por nivel de riesgo").contains("Mapa de calor")
            .contains("Figura 1").contains("Registro de riesgos").contains("Nivel de riesgo")
            .contains("Nota. El registro se ordena por nivel de riesgo (probabilidad × impacto) y no solo por severidad técnica.")
            .contains("Anexo A. Riesgos aceptados").contains("Activo").contains("Resp.");
        // Orden del registro: nivel de riesgo primero (Crítico antes que Alto antes que Medio).
        assertThat(texto.indexOf("H-D4E5F6")).isLessThan(texto.indexOf("H-A1B2C3"));
        assertThat(texto.indexOf("H-A1B2C3")).isLessThan(texto.indexOf("H-A7B8C9"));
    }

    @Test
    void elFinalTieneLasDosPartesTrazabilidadConstanciaYListaDeVerificacion() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.FIN, false);
        String texto = normalizar(PdfPrueba.texto(renderizar(snapshot, false)));
        assertThat(texto).contains("Parte I. Informe ejecutivo").contains("Carta de entrega")
            .contains("Parte II. Informe técnico").contains("Plan de mitigación").contains("Conclusiones")
            .contains("Paquete de entregables").contains("Constancia de entrega")
            .contains("Cuentas temporales eliminadas").contains("Archivos, payloads y binarios retirados")
            .contains("Configuraciones revertidas").contains("Servicio verificado sin degradación")
            .contains("Firma del auditor").contains("Firma del representante del cliente")
            .contains("Mapa de calor de riesgo");
        String sinEspacios = texto.replace(" ", "");
        for (var traza : snapshot.trazabilidad()) {
            assertThat(sinEspacios).contains(traza.id()).contains(traza.sha256().substring(0, 20));
        }
        assertThat(texto).contains("Anexo A. Riesgos aceptados").contains("Referencias");
    }

    @Test
    void elHistorialDeVersionesYElControlAparecenEnTodosLosTipos() throws Exception {
        for (TipoInforme tipo : TipoInforme.values()) {
            String texto = normalizar(PdfPrueba.texto(renderizar(DatosDemo.snapshot(tipo, false), false)));
            assertThat(texto).as(tipo.name()).contains("Control del documento").contains("Historial de versiones")
                .contains("Despublicado").contains("12 de noviembre de 2026").contains("14 de noviembre de 2026")
                .contains("Se corrigió la clasificación de dos hallazgos tras la revisión con el cliente.")
                .contains("Versión de la plantilla").contains("2026.1").contains("SHA-256 del contenido")
                .contains("La versión de referencia de este informe es el PDF publicado en TinyHat.")
                .contains("No constituye un dictamen legal ni una certificación de seguridad.");
        }
    }

    @Test
    void sinAceptadosNoApareceElRecuadroNiElAnexoA() throws Exception {
        var sinAceptadas = DatosDemo.vulnerabilidades().stream().filter(v -> !"Aceptada".equals(v.estado())).toList();
        for (TipoInforme tipo : TipoInforme.values()) {
            String texto = normalizar(PdfPrueba.texto(renderizar(
                DatosDemo.snapshotConVulnerabilidades(tipo, sinAceptadas), false)));
            assertThat(texto).as(tipo.name()).doesNotContain("Importante: este proyecto incluye")
                .doesNotContain("Anexo A. Riesgos aceptados");
        }
    }

    @ParameterizedTest
    @EnumSource(TipoInforme.class)
    void unProyectoSinHallazgosTambienSeGenera(TipoInforme tipo) throws Exception {
        byte[] pdf = renderizar(DatosDemo.snapshotSinHallazgos(tipo), false);
        assertThat(PdfPrueba.paginas(pdf)).isGreaterThanOrEqualTo(2);
        assertThat(normalizar(PdfPrueba.texto(pdf))).contains("no incluye hallazgos");
    }

    // ---------------------------------------------------------------- registro de renderizadores

    @Test
    void elRegistroResuelveElPdfDeLosCincoTiposYRechazaLoQueNoEsValido() {
        var registro = new com.hexora.hexora.informes.render.RegistroRenderers(List.of(MuestrasPdfTest.renderizador()));
        for (TipoInforme tipo : TipoInforme.values()) {
            assertThat(registro.resolver(tipo, FormatoInforme.PDF)).isInstanceOf(RenderizadorPdf.class);
        }
        // Combinaciones fuera de la lista blanca de §1.2.
        assertThatThrownBy(() -> registro.resolver(TipoInforme.MR, FormatoInforme.DOCX))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro.resolver(TipoInforme.TEC, FormatoInforme.XLSX))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registro.resolver(TipoInforme.EJE, FormatoInforme.XLSX))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elRenderizadorSoloAceptaPdf() {
        var renderizador = MuestrasPdfTest.renderizador();
        assertThat(renderizador.formato()).isEqualTo(FormatoInforme.PDF);
        for (TipoInforme tipo : TipoInforme.values()) {
            assertThat(renderizador.soporta(tipo)).isTrue();
        }
    }

    @Test
    void generaElPdfSoloAPartirDelSnapshotYSuHashEsReproducible() throws Exception {
        InformeSnapshot snapshot = DatosDemo.snapshot(TipoInforme.TEC, false);
        byte[] a = renderizar(snapshot, false);
        byte[] b = renderizar(snapshot, false);
        assertThat(normalizar(PdfPrueba.texto(a))).isEqualTo(normalizar(PdfPrueba.texto(b)));
        assertThat(PdfPrueba.paginas(a)).isEqualTo(PdfPrueba.paginas(b));
        // El contenido sale del snapshot guardado: un snapshot leído de su JSON produce el mismo texto.
        InformeSnapshot leido = SerializadorSnapshot.leer(SerializadorSnapshot.serializar(snapshot));
        assertThat(normalizar(PdfPrueba.texto(renderizar(leido, false)))).isEqualTo(normalizar(PdfPrueba.texto(a)));
        assertThat(new ByteArrayOutputStream().size()).isZero();
        assertThat(OpcionesRender.NO_PUBLICADO.noPublicado()).isTrue();
    }
}
