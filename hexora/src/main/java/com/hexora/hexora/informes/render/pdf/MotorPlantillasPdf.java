package com.hexora.hexora.informes.render.pdf;

import com.hexora.hexora.informes.TipoInforme;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Motor de plantillas <b>propio</b> de los informes (§7.1), separado del de la web: resuelve solo desde el classpath
 * ({@code informes/pdf/plantillas/}) en modo XML, para que la salida sea XHTML bien formado como exige el PDF, y
 * escapa todo lo que se imprime con {@code th:text}. No se expone como bean para no desplazar al motor de la web.
 */
@Component
public class MotorPlantillasPdf {

    static final String PREFIJO = "informes/pdf/plantillas/";

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    private final SpringTemplateEngine motor;

    public MotorPlantillasPdf() {
        ClassLoaderTemplateResolver resolvedor = new ClassLoaderTemplateResolver();
        resolvedor.setPrefix(PREFIJO);
        resolvedor.setSuffix(".html");
        resolvedor.setTemplateMode(TemplateMode.XML);
        resolvedor.setCharacterEncoding("UTF-8");
        resolvedor.setCacheable(true);
        resolvedor.setCheckExistence(true);
        this.motor = new SpringTemplateEngine();
        this.motor.setTemplateResolver(resolvedor);
    }

    /** Nombre de la plantilla de cada tipo. */
    public static String plantillaDe(TipoInforme tipo) {
        return switch (tipo) {
            case EJE -> "ejecutivo";
            case TEC -> "tecnico";
            case MIT -> "mitigacion";
            case MR -> "matriz";
            case FIN -> "final";
        };
    }

    /** Genera el XHTML del documento. La vista llega como variable {@code v}. */
    public String procesar(String plantilla, VistaPdf vista) {
        Context contexto = new Context(ES_CO);
        contexto.setVariable("v", vista);
        return motor.process(plantilla, contexto);
    }
}
