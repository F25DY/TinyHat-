package com.hexora.hexora.controller;

import com.hexora.hexora.security.TinyHatPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** "Volver al panel": cada rol llega a su inicio (el auditor a su panel, el administrador de la MiPyme al suyo). */
@Controller
public class InicioController {

    @GetMapping("/panel")
    @PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
    public String panel(@AuthenticationPrincipal TinyHatPrincipal principal) {
        if ("CLIENTE_ADMIN".equals(principal.getRol())) {
            return principal.isDebeCambiarPassword() ? "redirect:/cliente/contrasena" : "redirect:/cliente";
        }
        return "redirect:/dashboard";
    }
}
