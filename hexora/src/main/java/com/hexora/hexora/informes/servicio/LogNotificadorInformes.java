package com.hexora.hexora.informes.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Notificador por defecto (aún no hay SMTP configurado para los informes): solo escribe en el registro, sin correos
 * ni nombres. Se reemplaza por {@link SmtpNotificadorInformes} con {@code tinyhat.informes.notificacion.habilitada=true}.
 */
@Component
@ConditionalOnProperty(prefix = "tinyhat.informes.notificacion", name = "habilitada", havingValue = "false",
    matchIfMissing = true)
public class LogNotificadorInformes implements NotificadorInformes {

    private static final Logger LOG = LoggerFactory.getLogger(LogNotificadorInformes.class);

    @Override
    public void informePublicado(InformePublicadoEvento evento) {
        LOG.info("Informe publicado (notificación deshabilitada): versión={}, organización={}", evento.versionPublicId(),
            evento.organizacionId());
    }
}
