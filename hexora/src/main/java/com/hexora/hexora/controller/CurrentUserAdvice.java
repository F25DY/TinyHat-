package com.hexora.hexora.controller;

import com.hexora.hexora.security.TinyHatPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expone a todas las páginas quién ha iniciado sesión, para el menú lateral. Solo el correo y una etiqueta del rol:
 * nada que permita llegar a otros datos. En una página pública no hay usuario y los atributos quedan sin valor.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("usuarioActual")
    public String usuarioActual(@AuthenticationPrincipal TinyHatPrincipal principal) {
        return principal == null ? null : principal.getUsername();
    }

    @ModelAttribute("esCliente")
    public boolean esCliente(@AuthenticationPrincipal TinyHatPrincipal principal) {
        return principal != null && "CLIENTE_ADMIN".equals(principal.getRol());
    }

    @ModelAttribute("rolActual")
    public String rolActual(@AuthenticationPrincipal TinyHatPrincipal principal) {
        if (principal == null) {
            return null;
        }
        return "CLIENTE_ADMIN".equals(principal.getRol()) ? "Administrador de la organización" : "Auditor";
    }
}
