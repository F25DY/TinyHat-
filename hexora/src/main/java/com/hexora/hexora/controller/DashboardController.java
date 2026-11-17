package com.hexora.hexora.controller;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
@PreAuthorize("hasAnyRole('AUDITOR', 'CLIENTE_ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/dashboard", "/inicio"})
    public String dashboard(@AuthenticationPrincipal TinyHatPrincipal principal, Model model) {
        // El administrador de la MiPyme tiene su propio panel.
        if ("CLIENTE_ADMIN".equals(principal.getRol())) {
            return principal.isDebeCambiarPassword() ? "redirect:/cliente/contrasena" : "redirect:/cliente";
        }
        model.addAttribute("resumen", dashboardService.resumen(principal));
        return "dashboard";
    }
}
