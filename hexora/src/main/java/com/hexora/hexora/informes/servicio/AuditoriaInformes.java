package com.hexora.hexora.informes.servicio;

import com.hexora.hexora.security.AuditEventType;
import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.security.TinyHatPrincipal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Eventos de auditoria del modulo de informes (§8.6) sobre el mecanismo de la Fase 12. Cada evento lleva correlationId,
 * cuenta, usuario, organizacion y el UUID de la version; nunca contenido de la narrativa ni de los hallazgos. Se
 * escriben en una transaccion propia (ver {@link AuditService#informeEvent}). Esta clase no toca el repositorio: solo
 * {@link AuditService} lo hace.
 */
@Component
public class AuditoriaInformes {

    private final AuditService auditoria;

    public AuditoriaInformes(AuditService auditoria) {
        this.auditoria = auditoria;
    }

    public void registrar(
        AuditEventType tipo, TinyHatPrincipal principal, String resultado, String versionPublicId,
        Map<String, Object> detalle
    ) {
        auditoria.informeEvent(tipo, principal, resultado, versionPublicId, detalle);
    }

    public void ok(AuditEventType tipo, TinyHatPrincipal principal, String versionPublicId, Map<String, Object> detalle) {
        registrar(tipo, principal, "OK", versionPublicId, detalle);
    }
}
