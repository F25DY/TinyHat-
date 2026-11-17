package com.hexora.hexora.informes.validacion;

import java.util.List;

/**
 * Resultado de validar un snapshot contra las condiciones de publicación (§4.5). Los faltantes son mensajes de
 * negocio, sin detalles internos.
 */
public record ResultadoValidacion(List<String> faltantes) {

    /** En las copias NP, cada faltante se imprime con este marcador en lugar de bloquear el documento. */
    public static final String PENDIENTE = "[PENDIENTE DE COMPLETAR POR EL AUDITOR]";

    public ResultadoValidacion {
        faltantes = List.copyOf(faltantes);
    }

    public boolean esValido() {
        return faltantes.isEmpty();
    }

    /** Para publicar: lanza con la lista de faltantes si hay alguno. */
    public void exigirValido() {
        if (!esValido()) {
            throw new InformeValidacionException(faltantes);
        }
    }
}
