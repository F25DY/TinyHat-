package com.hexora.hexora.security;

import com.hexora.hexora.repository.AsignacionClienteProyectoRepository;
import org.springframework.stereotype.Component;

/**
 * Regla de entrada del administrador de la MiPyme (CLIENTE_ADMIN): puede iniciar sesión y mantener su sesión mientras
 * tenga al menos un proyecto asignado y activo (acceso no retirado y proyecto distinto de «Completado»).
 *
 * <p>Una cuenta CLIENTE_ADMIN sin ninguna fila de asignación (creada por la invitación por correo anterior a este control)
 * se rige por la regla anterior; en cuanto el auditor le asigna un proyecto, o se la retira, deja de aplicarse esa excepción
 * porque la fila queda como historia. Los demás roles no se ven afectados.
 */
@Component
public class AccesoClienteProyecto {

    private final AsignacionClienteProyectoRepository asignaciones;

    public AccesoClienteProyecto(AsignacionClienteProyectoRepository asignaciones) {
        this.asignaciones = asignaciones;
    }

    public boolean permitido(String rol, Long usuarioId, Long cuentaId) {
        if (!"CLIENTE_ADMIN".equals(rol)) {
            return true;
        }
        if (asignaciones.contarTodas(usuarioId, cuentaId) == 0) {
            return true;
        }
        return asignaciones.contarActivas(usuarioId, cuentaId) > 0;
    }
}
