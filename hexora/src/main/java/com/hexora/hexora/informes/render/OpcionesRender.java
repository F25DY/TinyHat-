package com.hexora.hexora.informes.render;

/**
 * Opciones de un renderizado. {@code noPublicado} marca las copias de trabajo: marca de agua o encabezado rojo, aviso
 * de "documento no publicado" y los faltantes impresos como "[PENDIENTE DE COMPLETAR POR EL AUDITOR]".
 */
public record OpcionesRender(boolean noPublicado) {

    public static final OpcionesRender PUBLICADO = new OpcionesRender(false);
    public static final OpcionesRender NO_PUBLICADO = new OpcionesRender(true);
}
