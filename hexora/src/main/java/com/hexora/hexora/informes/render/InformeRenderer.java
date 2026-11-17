package com.hexora.hexora.informes.render;

import com.hexora.hexora.informes.FormatoInforme;
import com.hexora.hexora.informes.TipoInforme;
import com.hexora.hexora.informes.snapshot.InformeSnapshot;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Convierte un {@link InformeSnapshot} en un archivo. El snapshot es la <b>única</b> entrada: un renderizador nunca
 * consulta tablas vivas ni la red, y escapa todo texto de usuario para su formato.
 */
public interface InformeRenderer {

    FormatoInforme formato();

    /** Aplica la tabla de §1.2: qué tipos produce este renderizador. */
    boolean soporta(TipoInforme tipo);

    void render(InformeSnapshot snapshot, OpcionesRender opciones, OutputStream salida) throws IOException;
}
