package com.hexora.hexora.informes.servicio;

import com.hexora.hexora.informes.servicio.NotificadorInformes.InformePublicadoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispara la notificación solo después de confirmar la publicación y fuera del hilo de la petición. Si la notificación
 * falla, <b>no</b> afecta a la publicación: solo se registra (sin datos del informe).
 */
@Component
public class EscuchaPublicacionInforme {

    private static final Logger LOG = LoggerFactory.getLogger(EscuchaPublicacionInforme.class);

    private final NotificadorInformes notificador;

    public EscuchaPublicacionInforme(NotificadorInformes notificador) {
        this.notificador = notificador;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alPublicar(InformePublicadoEvento evento) {
        try {
            notificador.informePublicado(evento);
        } catch (RuntimeException e) {
            LOG.warn("No se pudo enviar el aviso de informe publicado ({})", e.getClass().getSimpleName());
        }
    }

    /** Habilita {@code @Async} solo para este escucha; no hay otros usos en la aplicación. */
    @Configuration
    @EnableAsync
    static class HabilitaAsincronia {
    }
}
