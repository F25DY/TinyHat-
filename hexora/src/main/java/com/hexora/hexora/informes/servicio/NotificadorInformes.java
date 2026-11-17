package com.hexora.hexora.informes.servicio;

/**
 * Avisa a los CLIENTE_ADMIN de la organización de que hay un informe nuevo (§8.5). Las implementaciones nunca ponen el
 * nombre del proyecto, hallazgos ni adjuntos en el mensaje.
 */
public interface NotificadorInformes {

    void informePublicado(InformePublicadoEvento evento);

    /**
     * Lo que se publica tras confirmar la transacción de publicación. Solo identificadores: ni nombres ni contenido.
     */
    record InformePublicadoEvento(Long cuentaId, Long organizacionId, String versionPublicId) {
    }
}
