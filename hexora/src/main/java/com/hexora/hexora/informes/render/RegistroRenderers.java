package com.hexora.hexora.informes.render;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resuelve el renderizador de un tipo y formato. Si no existe (combinación fuera de la lista blanca de §1.2 o formato
 * todavía sin renderizador), lanza {@link IllegalArgumentException}, que la aplicación responde como un 400 genérico.
 */
@Component
public class RegistroRenderers {

    private final List<InformeRenderer> renderizadores;

    public RegistroRenderers(List<InformeRenderer> renderizadores) {
        this.renderizadores = List.copyOf(renderizadores);
    }

    public InformeRenderer resolver(TipoInforme tipo, FormatoInforme formato) {
        tipo.requerirFormato(formato);
        return renderizadores.stream()
            .filter(renderizador -> renderizador.formato() == formato && renderizador.soporta(tipo))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No hay renderizador para esa combinación"));
    }
}
