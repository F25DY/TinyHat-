package com.hexora.hexora.informes.validacion;

import java.util.List;

/**
 * Un informe no se puede ensamblar o publicar. Los mensajes son de negocio ("Falta la sección Resumen ejecutivo"):
 * nunca incluyen detalles internos, rutas ni trazas, así que se pueden mostrar al auditor tal cual.
 */
public class InformeValidacionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<String> mensajes;

    public InformeValidacionException(List<String> mensajes) {
        super(String.join("; ", mensajes));
        this.mensajes = List.copyOf(mensajes);
    }

    public InformeValidacionException(String mensaje) {
        this(List.of(mensaje));
    }

    public List<String> mensajes() {
        return mensajes;
    }
}
