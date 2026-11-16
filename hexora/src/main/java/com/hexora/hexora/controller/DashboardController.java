package com.hexora.hexora.controller;

import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
@PreAuthorize("hasRole('AUDITOR')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"/dashboard", "/inicio"})
    public String dashboard(@AuthenticationPrincipal TinyHatPrincipal principal, Model model) {
        model.addAttribute("resumen", dashboardService.resumen(principal));
        return "dashboard";
    }
}
