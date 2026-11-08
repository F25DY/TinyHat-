package com.hexora.network.controller;

import com.hexora.network.model.NetworkDashboardDto;
import com.hexora.network.model.NetworkDetectedDeviceDto;
import com.hexora.network.model.NetworkDiscoverySnapshotDto;
import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.model.NetworkHostDto;
import com.hexora.network.model.MonitoredPortDto;
import com.hexora.network.model.MonitoredPortRequest;
import com.hexora.network.model.NetworkHostPortsRequest;
import com.hexora.network.model.NetworkHostRegistrationRequest;
import com.hexora.network.model.NetworkScanDto;
import com.hexora.network.model.NetworkScanDeletionRequest;
import com.hexora.network.model.NetworkScanRequest;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.websocket.NetworkMonitorWebSocketHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/network")
@PreAuthorize("hasRole('AUDITOR')")
public class NetworkMonitoringController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitoringController.class);
    private final NetworkMonitoringService monitoringService;
    private final NetworkMonitorWebSocketHandler webSocketHandler;

    public NetworkMonitoringController(
        NetworkMonitoringService monitoringService,
        NetworkMonitorWebSocketHandler webSocketHandler
    ) {
        this.monitoringService = monitoringService;
        this.webSocketHandler = webSocketHandler;
    }

    @PostMapping("/scan")
    public ResponseEntity<NetworkScanDto> scan(
        @RequestBody NetworkScanRequest request,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null || request.hostId() == null) {
            throw new IllegalArgumentException("hostId is required");
        }
        NetworkScanDto result = monitoringService.scanManually(
            request.hostId(),
            request.startPort(),
            request.endPort()
        );
        webSocketHandler.publish(result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/history")
    public List<NetworkScanDto> history(
        @RequestParam(required = false) Long hostId,
        @RequestParam(defaultValue = "20") int limit,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        return monitoringService.getHistory(hostId, limit);
    }

    @GetMapping({"/hosts", "/devices"})
    public List<NetworkHostDto> hosts(HttpServletRequest httpRequest) {
        logEndpoint("GET", httpRequest);
        return monitoringService.getHosts();
    }

    @PostMapping({"/hosts", "/devices"})
    public ResponseEntity<NetworkHostDto> registerHost(
        @RequestBody NetworkHostRegistrationRequest request,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Device details are required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
            monitoringService.registerHost(request.name(), request.address(), request.ports())
        );
    }

    @PutMapping({"/hosts/{hostId}/ports", "/devices/{hostId}/ports"})
    public NetworkHostDto updateMonitoredPorts(
        @PathVariable Long hostId,
        @RequestBody NetworkHostPortsRequest request,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("PUT", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Port list is required");
        }
        return monitoringService.updateMonitoredPorts(hostId, request.ports());
    }

    @PostMapping({"/hosts/{hostId}/ports", "/devices/{hostId}/ports"})
    public ResponseEntity<MonitoredPortDto> addMonitoredPort(
        @PathVariable Long hostId,
        @RequestBody MonitoredPortRequest request,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Port is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(monitoringService.addMonitoredPort(hostId, request.port()));
    }

    @PutMapping({"/hosts/{hostId}/ports/{port}", "/devices/{hostId}/ports/{port}"})
    public MonitoredPortDto setMonitoredPortEnabled(
        @PathVariable Long hostId,
        @PathVariable Integer port,
        @RequestBody MonitoredPortRequest request,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("PUT", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Port monitoring state is required");
        }
        return monitoringService.setMonitoredPortEnabled(hostId, port, request.enabled());
    }

    @DeleteMapping({"/hosts/{hostId}/ports/{port}", "/devices/{hostId}/ports/{port}"})
    public ResponseEntity<Void> removeMonitoredPort(
        @PathVariable Long hostId,
        @PathVariable Integer port,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        monitoringService.removeMonitoredPort(hostId, port);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping({"/hosts/{hostId}", "/devices/{hostId}"})
    public ResponseEntity<Void> removeManagedHost(
        @PathVariable Long hostId,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        monitoringService.removeManagedHost(hostId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(HttpServletRequest httpRequest) {
        logEndpoint("DELETE", httpRequest);
        monitoringService.clearHistory();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history/scans")
    public ResponseEntity<Void> deleteSelectedScans(
        @RequestBody NetworkScanDeletionRequest request,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        monitoringService.deleteSelectedScans(request == null ? null : request.scanIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{hostId}/history")
    public ResponseEntity<Void> clearHostHistory(
        @PathVariable Long hostId,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        monitoringService.clearHostHistory(hostId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/discover")
    public NetworkInventoryUpdateDto discoverLocalNetwork(HttpServletRequest httpRequest) {
        logEndpoint("POST", httpRequest);
        NetworkInventoryUpdateDto update = monitoringService.discoverLocalNetwork();
        if (update.changed()) {
            webSocketHandler.publish(update);
        }
        return update;
    }

    @GetMapping("/detected-devices")
    public List<NetworkDetectedDeviceDto> detectedDevices(HttpServletRequest httpRequest) {
        logEndpoint("GET", httpRequest);
        return monitoringService.getDetectedDevices();
    }

    @GetMapping("/gateway")
    public ResponseEntity<NetworkDetectedDeviceDto> gateway(HttpServletRequest httpRequest) {
        logEndpoint("GET", httpRequest);
        return monitoringService.findGateway()
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/discoveries/latest")
    public ResponseEntity<NetworkDiscoverySnapshotDto> latestDiscovery(HttpServletRequest httpRequest) {
        logEndpoint("GET", httpRequest);
        Optional<NetworkDiscoverySnapshotDto> snapshot = monitoringService.getLatestDiscoverySnapshot();
        return snapshot.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/discoveries/history")
    public List<NetworkDiscoverySnapshotDto> discoveryHistory(
        @RequestParam(defaultValue = "50") int limit,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        return monitoringService.getDiscoveryHistory(limit);
    }

    @GetMapping("/status")
    public NetworkDashboardDto status(HttpServletRequest httpRequest) {
        logEndpoint("GET", httpRequest);
        return monitoringService.getStatus();
    }

    private void logEndpoint(String expectedMethod, HttpServletRequest request) {
        LOGGER.info("Network endpoint {}: expected HTTP method {}, received {}",
            request.getRequestURI(), expectedMethod, request.getMethod());
    }
}
