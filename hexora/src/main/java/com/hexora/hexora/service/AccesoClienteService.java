package com.hexora.hexora.service;

import com.hexora.hexora.model.AsignacionAuditorProyectoId;
import com.hexora.hexora.model.AsignacionClienteProyecto;
import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.model.Usuario;
import com.hexora.hexora.repository.AsignacionClienteProyectoRepository;
import com.hexora.hexora.repository.UsuarioRepository;
import com.hexora.hexora.security.AuditEventType;
import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.security.PasswordPolicyValidator;
import com.hexora.hexora.security.ScopedNotFoundException;
import com.hexora.hexora.security.TenantContext;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.security.UserRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acceso del administrador de la MiPyme (CLIENTE_ADMIN) a un proyecto. El AUDITOR asignado lo agrega con una contraseña
 * temporal que se muestra una sola vez para que se la entregue; el cliente debe cambiarla al iniciar sesión. Puede
 * entrar mientras tenga un proyecto asignado y activo. Nunca se guarda ni se audita una contraseña en claro.
 */
@Service
public class AccesoClienteService {

    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Contraseña temporal: solo existe en la respuesta que ve el auditor. {@code cuentaNueva} false: ya tenía cuenta. */
    public record Credencial(String correo, String contrasenaTemporal, boolean cuentaNueva, String proyecto) {
    }

    public record AccesoVista(
        String usuarioPublicId, String correo, boolean vigente, boolean debeCambiar, LocalDateTime concedidoEn,
        LocalDateTime retiradoEn
    ) {
    }

    private final PermissionService permisos;
    private final UsuarioRepository usuarios;
    private final AsignacionClienteProyectoRepository asignaciones;
    private final PasswordEncoder codificador;
    private final PasswordPolicyValidator politica;
    private final AuditService auditoria;
    private final UserRegistrationService registro;
    private final Clock reloj;

    public AccesoClienteService(
        PermissionService permisos,
        UsuarioRepository usuarios,
        AsignacionClienteProyectoRepository asignaciones,
        PasswordEncoder codificador,
        PasswordPolicyValidator politica,
        AuditService auditoria,
        UserRegistrationService registro,
        Clock reloj
    ) {
        this.permisos = permisos;
        this.usuarios = usuarios;
        this.asignaciones = asignaciones;
        this.codificador = codificador;
        this.politica = politica;
        this.auditoria = auditoria;
        this.registro = registro;
        this.reloj = reloj;
    }

    // ================================================================ el AUDITOR gestiona el acceso

    /** Agrega al administrador de la MiPyme al proyecto; crea su cuenta con una contraseña temporal si no la tiene. */
    @Transactional
    public Credencial conceder(
        TinyHatPrincipal auditor, String proyectoPublicId, String correo, HttpServletRequest solicitud
    ) {
        Auditoria proyecto = proyectoDelAuditor(auditor, proyectoPublicId);
        String email = normalizar(correo);
        Long organizacion = proyecto.getEmpresa().getId();
        Optional<Usuario> existente = usuarios.findByEmailNormalizado(email);
        if (existente.isPresent()) {
            Usuario u = existente.get();
            boolean mismoCliente = "CLIENTE_ADMIN".equals(u.getRol()) && u.getCuentaId().equals(proyecto.getCuentaId())
                && organizacion.equals(u.getOrganizacionId());
            if (!mismoCliente) {
                throw new IllegalArgumentException("No se puede dar acceso a ese correo.");
            }
            vincular(u, proyecto, auditor);
            auditoria.clienteAcceso(AuditEventType.CLIENTE_ACCESO_CONCEDIDO, auditor, solicitud, u, proyecto.getPublicId());
            return new Credencial(email, null, false, proyecto.getNombre());
        }
        String temporal = contrasenaTemporal();
        Usuario creado = TenantContext.runAs(proyecto.getCuentaId(), () -> {
            Usuario u = new Usuario();
            u.setCuentaId(proyecto.getCuentaId());
            u.setOrganizacionId(organizacion);
            u.setRol("CLIENTE_ADMIN");
            u.setEstado("ACTIVO");
            u.setEmailNormalizado(email);
            u.setPasswordHash(codificador.encode(temporal));
            u.setDebeCambiarPassword(true);
            return usuarios.saveAndFlush(u);
        });
        vincular(creado, proyecto, auditor);
        auditoria.userCreated(creado, solicitud);
        auditoria.clienteAcceso(AuditEventType.CLIENTE_ACCESO_CONCEDIDO, auditor, solicitud, creado, proyecto.getPublicId());
        return new Credencial(email, temporal, true, proyecto.getNombre());
    }

    /** Nueva contraseña temporal para quien perdió la suya o aún no entró; cierra sus sesiones y le exige cambiarla. */
    @Transactional
    public Credencial restablecer(
        TinyHatPrincipal auditor, String proyectoPublicId, String usuarioPublicId, HttpServletRequest solicitud
    ) {
        Auditoria proyecto = proyectoDelAuditor(auditor, proyectoPublicId);
        Usuario cliente = clienteDelProyecto(proyecto, usuarioPublicId, true);
        String temporal = contrasenaTemporal();
        cliente.setPasswordHash(codificador.encode(temporal));
        cliente.setDebeCambiarPassword(true);
        usuarios.saveAndFlush(cliente);
        registro.expireSessionsForUser(cliente.getId());
        auditoria.clienteAcceso(AuditEventType.CLIENTE_ACCESO_RESTABLECIDO, auditor, solicitud, cliente, proyecto.getPublicId());
        return new Credencial(cliente.getEmailNormalizado(), temporal, false, proyecto.getNombre());
    }

    /** Retira el acceso a este proyecto (la cuenta y la historia se conservan); cierra sus sesiones. */
    @Transactional
    public void retirar(
        TinyHatPrincipal auditor, String proyectoPublicId, String usuarioPublicId, HttpServletRequest solicitud
    ) {
        Auditoria proyecto = proyectoDelAuditor(auditor, proyectoPublicId);
        Usuario cliente = clienteDelProyecto(proyecto, usuarioPublicId, false);
        AsignacionClienteProyecto fila = asignaciones.findById(
            new AsignacionAuditorProyectoId(cliente.getId(), proyecto.getIdAuditoria(), proyecto.getCuentaId()))
            .orElseThrow(ScopedNotFoundException::new);
        fila.retirar(ahora());
        asignaciones.saveAndFlush(fila);
        registro.expireSessionsForUser(cliente.getId());
        auditoria.clienteAcceso(AuditEventType.CLIENTE_ACCESO_RETIRADO, auditor, solicitud, cliente, proyecto.getPublicId());
    }

    @Transactional(readOnly = true)
    public List<AccesoVista> listar(TinyHatPrincipal auditor, String proyectoPublicId) {
        Auditoria proyecto = proyectoDelAuditor(auditor, proyectoPublicId);
        List<AsignacionClienteProyecto> filas = asignaciones.delProyecto(proyecto.getIdAuditoria(), proyecto.getCuentaId());
        Map<Long, Usuario> porId = asignaciones.usuarios(
            filas.stream().map(f -> f.getId().getUsuarioId()).toList(), proyecto.getCuentaId()).stream()
            .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        List<AccesoVista> lista = new ArrayList<>();
        for (AsignacionClienteProyecto fila : filas) {
            Usuario u = porId.get(fila.getId().getUsuarioId());
            if (u != null) {
                lista.add(new AccesoVista(u.getPublicId(), u.getEmailNormalizado(), fila.estaVigente(),
                    u.isDebeCambiarPassword(), fila.getCreadoEn(), fila.getRetiradoEn()));
            }
        }
        return lista;
    }

    /** Nombre del proyecto y su estado, para la cabecera de la pantalla de acceso. */
    @Transactional(readOnly = true)
    public Auditoria proyecto(TinyHatPrincipal auditor, String proyectoPublicId) {
        return proyectoDelAuditor(auditor, proyectoPublicId);
    }

    // ================================================================ el cliente cambia su contraseña

    /**
     * El cliente ya con sesión cambia su contraseña: debe dar la actual, la nueva cumple la política y es distinta. Se
     * cierran todas sus sesiones (también la actual): vuelve a entrar con la nueva.
     */
    @Transactional
    public void cambiarContrasena(
        TinyHatPrincipal cliente, String actual, String nueva, HttpServletRequest solicitud
    ) {
        if (cliente == null || !"CLIENTE_ADMIN".equals(cliente.getRol())) {
            throw new ScopedNotFoundException();
        }
        Usuario u = usuarios.findByIdAndCuentaId(cliente.getUserId(), cliente.getCuentaId())
            .orElseThrow(ScopedNotFoundException::new);
        if (actual == null || !codificador.matches(actual, u.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        politica.validate(nueva, u.getEmailNormalizado());
        if (codificador.matches(nueva, u.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña nueva debe ser distinta de la actual.");
        }
        u.setPasswordHash(codificador.encode(nueva));
        u.setDebeCambiarPassword(false);
        usuarios.saveAndFlush(u);
        registro.expireSessionsForUser(u.getId());
        auditoria.passwordChanged(u, solicitud, "CLIENT_CHANGE");
    }

    // ================================================================ utilidades

    private Auditoria proyectoDelAuditor(TinyHatPrincipal auditor, String proyectoPublicId) {
        if (auditor == null || !"AUDITOR".equals(auditor.getRol())) {
            throw new ScopedNotFoundException();
        }
        return permisos.requireProyecto(auditor, proyectoPublicId);
    }

    private void vincular(Usuario cliente, Auditoria proyecto, TinyHatPrincipal auditor) {
        AsignacionAuditorProyectoId id = new AsignacionAuditorProyectoId(
            cliente.getId(), proyecto.getIdAuditoria(), proyecto.getCuentaId());
        Optional<AsignacionClienteProyecto> fila = asignaciones.findById(id);
        if (fila.isPresent()) {
            fila.get().reactivar();
            asignaciones.saveAndFlush(fila.get());
        } else {
            asignaciones.saveAndFlush(new AsignacionClienteProyecto(id, auditor.getUserId(), ahora()));
        }
    }

    /** El usuario debe tener acceso (vigente o, si {@code soloVigente} es false, también retirado) a este proyecto. */
    private Usuario clienteDelProyecto(Auditoria proyecto, String usuarioPublicId, boolean soloVigente) {
        Usuario u = usuarios.findByPublicIdAndCuentaId(usuarioPublicId, proyecto.getCuentaId())
            .orElseThrow(ScopedNotFoundException::new);
        AsignacionClienteProyecto fila = asignaciones.findById(
            new AsignacionAuditorProyectoId(u.getId(), proyecto.getIdAuditoria(), proyecto.getCuentaId()))
            .orElseThrow(ScopedNotFoundException::new);
        if (soloVigente && !fila.estaVigente()) {
            throw new ScopedNotFoundException();
        }
        return u;
    }

    private static String normalizar(String correo) {
        String limpio = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        if (limpio.length() > 254 || !CORREO.matcher(limpio).matches()) {
            throw new IllegalArgumentException("Escriba un correo válido.");
        }
        return limpio;
    }

    /** 16 caracteres sin ambiguos (sin 0/O/1/l/I), en cuatro grupos para dictarla: {@code Xk3m-Pq7R-aT9n-Wd4h}. */
    static String contrasenaTemporal() {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                texto.append('-');
            }
            texto.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return texto.toString();
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj.withZone(ZoneOffset.UTC));
    }
}
