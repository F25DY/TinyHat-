package com.hexora.hexora.informes;

/**
 * Estado de una versión de informe. Las únicas transiciones válidas son BORRADOR → PUBLICADO y
 * PUBLICADO → DESPUBLICADO. Un borrador puede eliminarse físicamente; una versión publicada o despublicada nunca.
 */
public enum EstadoVersion {

    BORRADOR,
    PUBLICADO,
    DESPUBLICADO;

    public boolean puedePasarA(EstadoVersion destino) {
        return (this == BORRADOR && destino == PUBLICADO)
            || (this == PUBLICADO && destino == DESPUBLICADO);
    }

    /** Solo un borrador puede borrarse; lo publicado queda como evidencia. */
    public boolean permiteEliminar() {
        return this == BORRADOR;
    }

    /** Solo un informe PUBLICADO lo ve y descarga el CLIENTE_ADMIN. */
    public boolean visibleParaCliente() {
        return this == PUBLICADO;
    }
}
