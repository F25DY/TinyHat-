package com.hexora.hexora.controller;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.AccesoClienteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * El AUDITOR asignado al proyecto gestiona quién de la MiPyme puede entrar: agrega al administrador (con una contraseña
 * temporal que se muestra una sola vez), la restablece o retira su acceso. Solo AUDITOR; un proyecto ajeno es un 404.
 */
@Controller
@Validated
@RequestMapping("/proyectos/{proyectoUuid}/acceso-cliente")
public class AccesoClienteController {

    private static final String UUID_RE = "^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$";

    private final AccesoClienteService servicio;

    public AccesoClienteController(AccesoClienteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('AUDITOR')")
    public String pantalla(
        @AuthenticationPrincipal TinyHatPrincipal auditor,
        @PathVariable @Pattern(regexp = UUID_RE) String proyectoUuid,
        Model modelo
    ) {
        modelo.addAttribute("proyecto", servicio.proyecto(auditor, proyectoUuid));
        modelo.addAttribute("accesos", servicio.listar(auditor, proyectoUuid));
        return "acceso-cliente";
    }

    @PostMapping
    @PreAuthorize("hasRole('AUDITOR')")
    public String conceder(
        @AuthenticationPrincipal TinyHatPrincipal auditor,
        @PathVariable @Pattern(regexp = UUID_RE) String proyectoUuid,
        @RequestParam @NotBlank @Size(max = 254) String correo,
        HttpServletRequest solicitud,
        RedirectAttributes aviso
    ) {
        try {
            var credencial = servicio.conceder(auditor, proyectoUuid, correo, solicitud);
            aviso.addFlashAttribute("credencial", credencial);
        } catch (IllegalArgumentException e) {
            aviso.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/proyectos/" + proyectoUuid + "/acceso-cliente";
    }

    @PostMapping("/{usuarioUuid}/restablecer")
    @PreAuthorize("hasRole('AUDITOR')")
    public String restablecer(
        @AuthenticationPrincipal TinyHatPrincipal auditor,
        @PathVariable @Pattern(regexp = UUID_RE) String proyectoUuid,
        @PathVariable @Pattern(regexp = UUID_RE) String usuarioUuid,
        HttpServletRequest solicitud,
        RedirectAttributes aviso
    ) {
        aviso.addFlashAttribute("credencial", servicio.restablecer(auditor, proyectoUuid, usuarioUuid, solicitud));
        return "redirect:/proyectos/" + proyectoUuid + "/acceso-cliente";
    }

    @PostMapping("/{usuarioUuid}/retirar")
    @PreAuthorize("hasRole('AUDITOR')")
    public String retirar(
        @AuthenticationPrincipal TinyHatPrincipal auditor,
        @PathVariable @Pattern(regexp = UUID_RE) String proyectoUuid,
        @PathVariable @Pattern(regexp = UUID_RE) String usuarioUuid,
        HttpServletRequest solicitud,
        RedirectAttributes aviso
    ) {
        servicio.retirar(auditor, proyectoUuid, usuarioUuid, solicitud);
        aviso.addFlashAttribute("mensaje", "Acceso retirado. Esa persona ya no puede entrar a este proyecto.");
        return "redirect:/proyectos/" + proyectoUuid + "/acceso-cliente";
    }
}
