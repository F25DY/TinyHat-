package com.hexora.network.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("hasRole('AUDITOR')")
public class NetworkMonitoringPageController {

    @GetMapping("/network-monitoring")
    public String networkMonitoring() {
        return "network-monitoring";
    }

    @GetMapping("/network-monitoring/history")
    public String networkDiscoveryHistory() {
        return "network-monitoring-history";
    }
}
