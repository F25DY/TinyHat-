package com.hexora.hexora.controller;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.AccesoClienteService;
import com.hexora.hexora.service.ClientePanelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Vista del administrador de la MiPyme: su panel, sus informes y el cambio de su contraseña. Solo CLIENTE_ADMIN. */
@Controller
@Validated
public class ClienteWebController {

    private final ClientePanelService panel;
    private final AccesoClienteService acceso;

    public ClienteWebController(ClientePanelService panel, AccesoClienteService acceso) {
        this.panel = panel;
        this.acceso = acceso;
    }

    @GetMapping("/cliente")
    @PreAuthorize("hasRole('CLIENTE_ADMIN')")
    public String inicio(@AuthenticationPrincipal TinyHatPrincipal cliente, Model modelo) {
        modelo.addAttribute("panel", panel.panel(cliente));
        return "cliente/panel";
    }

    @GetMapping("/cliente/informes")
    @PreAuthorize("hasRole('CLIENTE_ADMIN')")
    public String informes(@AuthenticationPrincipal TinyHatPrincipal cliente, Model modelo) {
        modelo.addAttribute("informes", panel.historial(cliente));
        return "informes/cliente";
    }

    @GetMapping("/cliente/contrasena")
    @PreAuthorize("hasRole('CLIENTE_ADMIN')")
    public String formulario(@AuthenticationPrincipal TinyHatPrincipal cliente, Model modelo) {
        modelo.addAttribute("obligatorio", cliente.isDebeCambiarPassword());
        return "cliente/contrasena";
    }

    @PostMapping("/cliente/contrasena")
    @PreAuthorize("hasRole('CLIENTE_ADMIN')")
    public String cambiar(
        @AuthenticationPrincipal TinyHatPrincipal cliente,
        @RequestParam @NotBlank @Size(max = 200) String actual,
        @RequestParam @NotBlank @Size(max = 200) String nueva,
        @RequestParam @NotBlank @Size(max = 200) String confirmar,
        HttpServletRequest solicitud,
        HttpSession sesion,
        RedirectAttributes aviso
    ) {
        if (!nueva.equals(confirmar)) {
            aviso.addFlashAttribute("error", "La confirmación no coincide con la contraseña nueva.");
            return "redirect:/cliente/contrasena";
        }
        try {
            acceso.cambiarContrasena(cliente, actual, nueva, solicitud);
        } catch (IllegalArgumentException e) {
            aviso.addFlashAttribute("error", e.getMessage());
            return "redirect:/cliente/contrasena";
        }
        // Se cierran todas sus sesiones: entra de nuevo con la contraseña nueva.
        SecurityContextHolder.clearContext();
        sesion.invalidate();
        return "redirect:/login?cambiada";
    }
}
