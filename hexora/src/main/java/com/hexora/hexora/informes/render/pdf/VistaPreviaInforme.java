package com.hexora.hexora.informes.render.pdf;

import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.render.OpcionesRender;
import com.hexora.hexora.informes.render.TextoSeguro;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import org.springframework.stereotype.Component;

/**
 * Vista previa en HTML de un informe: las mismas plantillas del PDF (con la marca de no publicado) pero con la hoja de
 * estilos y el logo servidos como archivos estáticos de la aplicación, de modo que se cumple la CSP sin estilos ni
 * scripts en línea. El texto de usuario sale escapado igual que en el PDF.
 */
@Component
public class VistaPreviaInforme {

    static final String CSS = "/css/informe-vista-previa.css";
    static final String LOGO = "/imagen/logo.png";

    private final ConstructorVistaInforme vistas;
    private final MotorPlantillasPdf motor;

    public VistaPreviaInforme(ConstructorVistaInforme vistas, MotorPlantillasPdf motor) {
        this.vistas = vistas;
        this.motor = motor;
    }

    public String html(InformeSnapshot snapshot) {
        TipoInforme tipo = TipoInforme.valueOf(snapshot.informe().tipo());
        String xhtml = TextoSeguro.limpiarXml(
            motor.procesar(MotorPlantillasPdf.plantillaDe(tipo), vistas.construir(snapshot, new OpcionesRender(true))));
        return xhtml
            .replaceAll("<link[^>]*href=\"classpath:informes/pdf/informe-horizontal\\.css\"[^>]*/>", "")
            .replace("classpath:informes/pdf/informe.css", CSS)
            .replace("classpath:informes/pdf/img/logo-tinyhat.png", LOGO);
    }
}
