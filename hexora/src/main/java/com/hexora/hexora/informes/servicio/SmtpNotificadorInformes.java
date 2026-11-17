package com.hexora.hexora.informes.servicio;

import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.security.Notifier;
import com.hexora.hexora.security.TenantTaskExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Envía el aviso con el {@link Notifier} existente a las cuentas CLIENTE_ADMIN activas de la organización. El mensaje
 * solo dice que hay un informe nuevo y da el enlace de inicio de sesión: sin proyecto, hallazgos ni adjuntos.
 */
@Component
@ConditionalOnProperty(prefix = "tinyhat.informes.notificacion", name = "habilitada", havingValue = "true")
public class SmtpNotificadorInformes implements NotificadorInformes {

    private final Notifier notifier;
    private final UsuarioRepository usuarios;
    private final TenantTaskExecutor tenants;
    private final String urlInicioSesion;

    public SmtpNotificadorInformes(
        Notifier notifier,
        UsuarioRepository usuarios,
        TenantTaskExecutor tenants,
        @Value("${tinyhat.security.public-base-url}") String urlPublica
    ) {
        this.notifier = notifier;
        this.usuarios = usuarios;
        this.tenants = tenants;
        this.urlInicioSesion = urlPublica.replaceAll("/+$", "") + "/login";
    }

    @Override
    public void informePublicado(InformePublicadoEvento evento) {
        AtomicReference<List<String>> destinatarios = new AtomicReference<>(List.of());
        tenants.execute(evento.cuentaId(), () -> {
            List<String> correos = new ArrayList<>();
            for (Usuario u : usuarios.findByCuentaIdAndOrganizacionIdAndRolAndEstado(
                evento.cuentaId(), evento.organizacionId(), "CLIENTE_ADMIN", "ACTIVO")) {
                correos.add(u.getEmailNormalizado());
            }
            destinatarios.set(correos);
        });
        for (String correo : destinatarios.get()) {
            notifier.sendReportAvailableEmail(correo, urlInicioSesion);
        }
    }
}
