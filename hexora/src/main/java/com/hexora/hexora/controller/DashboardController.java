package com.hexora.hexora.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
@PreAuthorize("hasRole('AUDITOR')")
public class DashboardController {

    @GetMapping({"/dashboard", "/inicio"})
    public String dashboard() {
        return "dashboard";
    }
}
