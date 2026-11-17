package com.hexora.hexora.informes.render.pdf;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.render.InformeRenderer;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.stereotype.Component;

/**
 * Renderizador PDF de los cinco tipos de informe. Del snapshot sale una vista (ya calculada y saneada), de la vista
 * el XHTML (plantillas propias, texto de usuario siempre escapado), y del XHTML el PDF (solo recursos del classpath).
 * No consulta datos vivos ni la red.
 */
@Component
public class RenderizadorPdf implements InformeRenderer {

    private final ConstructorVistaInforme vistas;
    private final MotorPlantillasPdf motor;

    public RenderizadorPdf(ConstructorVistaInforme vistas, MotorPlantillasPdf motor) {
        this.vistas = vistas;
        this.motor = motor;
    }

    @Override
    public FormatoInforme formato() {
        return FormatoInforme.PDF;
    }

    @Override
    public boolean soporta(TipoInforme tipo) {
        return tipo.admite(FormatoInforme.PDF);
    }

    @Override
    public void render(InformeSnapshot snapshot, OpcionesRender opciones, OutputStream salida) throws IOException {
        TipoInforme tipo = TipoInforme.valueOf(snapshot.informe().tipo());
        tipo.requerirFormato(FormatoInforme.PDF);
        String xhtml = MarcadoresPdf.agregar(sinCaracteresInvalidosEnXml(
            motor.procesar(MotorPlantillasPdf.plantillaDe(tipo), vistas.construir(snapshot, opciones))));
        VerificadorRecursosHtml.verificar(xhtml);
        GeneradorPdf.generar(xhtml, new ResolvedorRecursosSeguro(), salida);
    }

    /**
     * El texto de usuario sale escapado ({@code &}, {@code <}, {@code >}, comillas), pero un carácter de control o un
     * suplente suelto no es XML 1.0 válido y haría fallar el análisis del documento. Se eliminan.
     */
    static String sinCaracteresInvalidosEnXml(String texto) {
        return com.hexora.hexora.informes.render.TextoSeguro.limpiarXml(texto);
    }
}
