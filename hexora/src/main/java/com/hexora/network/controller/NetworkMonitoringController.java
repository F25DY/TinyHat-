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
import com.hexora.network.model.AuthorizedNetworkRangeDto;
import com.hexora.network.model.AuthorizedNetworkRangeRequest;
import com.hexora.network.model.ProjectNetworkRequest;
import com.hexora.network.model.NetworkProjectDto;
import com.hexora.network.service.AuthorizedNetworkRangeService;
import com.hexora.network.service.NetworkDiscoveryException;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.service.ScanRateLimitException;
import com.hexora.network.websocket.NetworkMonitorWebSocketHandler;
import com.hexora.hexora.security.TinyHatPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@PreAuthorize("hasRole('AUDITOR') and @networkCapabilityGuard.isEnabled(authentication)")
public class NetworkMonitoringController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitoringController.class);
    private final NetworkMonitoringService monitoringService;
    private final NetworkMonitorWebSocketHandler webSocketHandler;
    private final AuthorizedNetworkRangeService authorizedRangeService;

    public NetworkMonitoringController(
        NetworkMonitoringService monitoringService,
        NetworkMonitorWebSocketHandler webSocketHandler,
        AuthorizedNetworkRangeService authorizedRangeService
    ) {
        this.monitoringService = monitoringService;
        this.webSocketHandler = webSocketHandler;
        this.authorizedRangeService = authorizedRangeService;
    }

    @PostMapping("/authorized-ranges")
    public ResponseEntity<AuthorizedNetworkRangeDto> declareAuthorizedRange(
        @RequestBody AuthorizedNetworkRangeRequest request,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        AuthorizedNetworkRangeDto range = authorizedRangeService.declare(principal, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(range);
    }

    @GetMapping("/projects/{projectPublicId}/authorized-ranges")
    public List<AuthorizedNetworkRangeDto> authorizedRanges(
        @PathVariable String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        return authorizedRangeService.list(principal, projectPublicId);
    }

    @GetMapping("/projects")
    public List<NetworkProjectDto> visibleProjects(
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        return authorizedRangeService.visibleProjects(principal);
    }

    @PostMapping("/scan")
    public ResponseEntity<NetworkScanDto> scan(
        @RequestBody NetworkScanRequest request,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null || request.hostId() == null) {
            throw new IllegalArgumentException("hostId is required");
        }
        monitoringService.validateScanParameters(request.startPort(), request.endPort());
        if (request.projectPublicId() == null) {
            throw new IllegalArgumentException("projectPublicId is required");
        }
        long projectId = authorizedRangeService.requireProjectId(principal, request.projectPublicId());
        try {
            monitoringService.validateManualScanScope(request.hostId(), projectId, principal.getCuentaId());
            authorizedRangeService.recordNetworkEvent(
                "SCAN_STARTED", "OK", principal, httpRequest, request.projectPublicId(), "", "");
            NetworkScanDto result = monitoringService.scanManually(
                request.hostId(),
                projectId,
                request.startPort(),
                request.endPort(),
                principal.getUserId()
            );
            authorizedRangeService.recordNetworkEvent(
                "SCAN_COMPLETED", "OK", principal, httpRequest, request.projectPublicId(), "", "");
            webSocketHandler.publish(request.projectPublicId(), result);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (com.hexora.network.service.NetworkScopeRejectedException exception) {
            authorizedRangeService.recordNetworkEvent(
                "SCAN_REJECTED", "DENEGADO", principal, httpRequest,
                request.projectPublicId(), "", exception.getMessage());
            throw exception;
        } catch (ScanRateLimitException exception) {
            authorizedRangeService.recordNetworkEvent(
                "SCAN_REJECTED", "DENEGADO", principal, httpRequest,
                request.projectPublicId(), "", exception.getMessage());
            throw exception;
        } catch (NetworkDiscoveryException exception) {
            authorizedRangeService.recordNetworkEvent(
                "SCAN_FAILED", "ERROR", principal, httpRequest,
                request.projectPublicId(), "", exception.getMessage());
            throw exception;
        }
    }

    @GetMapping("/history")
    public List<NetworkScanDto> history(
        @RequestParam(required = false) Long hostId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.getHistory(hostId, limit, projectId);
    }

    @GetMapping({"/hosts", "/devices"})
    public List<NetworkHostDto> hosts(
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.getHosts(projectId);
    }

    @PostMapping({"/hosts", "/devices"})
    public ResponseEntity<NetworkHostDto> registerHost(
        @RequestBody NetworkHostRegistrationRequest request,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Device details are required");
        }
        monitoringService.validateRegistrationInput(request.name(), request.address(), request.ports());
        if (request.projectPublicId() == null) {
            throw new IllegalArgumentException("projectPublicId is required");
        }
        long projectId = authorizedRangeService.requireProjectId(principal, request.projectPublicId());
        authorizedRangeService.requireAuthorizedTarget(principal, projectId, request.address());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(monitoringService.registerHost(request.name(), request.address(), request.ports(), projectId));
    }

    @PutMapping({"/hosts/{hostId}/ports", "/devices/{hostId}/ports"})
    public NetworkHostDto updateMonitoredPorts(
        @PathVariable Long hostId,
        @RequestBody NetworkHostPortsRequest request,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("PUT", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Port list is required");
        }
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.updateMonitoredPorts(hostId, request.ports(), projectId);
    }

    @PostMapping({"/hosts/{hostId}/ports", "/devices/{hostId}/ports"})
    public ResponseEntity<MonitoredPortDto> addMonitoredPort(
        @PathVariable Long hostId,
        @RequestBody MonitoredPortRequest request,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Port is required");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(monitoringService.addMonitoredPort(
                hostId,
                request.port(),
                authorizedRangeService.requireProjectId(principal, projectPublicId)));
    }

    @PutMapping({"/hosts/{hostId}/ports/{port}", "/devices/{hostId}/ports/{port}"})
    public MonitoredPortDto setMonitoredPortEnabled(
        @PathVariable Long hostId,
        @PathVariable Integer port,
        @RequestBody MonitoredPortRequest request,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("PUT", httpRequest);
        if (request == null) {
            throw new IllegalArgumentException("Port monitoring state is required");
        }
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.setMonitoredPortEnabled(hostId, port, request.enabled(), projectId);
    }

    @DeleteMapping({"/hosts/{hostId}/ports/{port}", "/devices/{hostId}/ports/{port}"})
    public ResponseEntity<Void> removeMonitoredPort(
        @PathVariable Long hostId,
        @PathVariable Integer port,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        monitoringService.removeMonitoredPort(hostId, port, projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping({"/hosts/{hostId}", "/devices/{hostId}"})
    public ResponseEntity<Void> removeManagedHost(
        @PathVariable Long hostId,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        monitoringService.removeManagedHost(hostId, projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        monitoringService.clearProjectHistory(projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history/scans")
    public ResponseEntity<Void> deleteSelectedScans(
        @RequestBody NetworkScanDeletionRequest request,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        monitoringService.deleteSelectedScans(request == null ? null : request.scanIds(), projectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{hostId}/history")
    public ResponseEntity<Void> clearHostHistory(
        @PathVariable Long hostId,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("DELETE", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        monitoringService.clearHostHistory(hostId, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/discover")
    public NetworkInventoryUpdateDto discoverLocalNetwork(
        @RequestBody ProjectNetworkRequest request,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("POST", httpRequest);
        if (request == null || request.projectPublicId() == null) {
            throw new IllegalArgumentException("projectPublicId is required");
        }
        long projectId = authorizedRangeService.requireProjectId(principal, request.projectPublicId());
        try {
            authorizedRangeService.requireAuthorizedInterface(principal, projectId);
            authorizedRangeService.recordNetworkEvent(
                "SCAN_STARTED", "OK", principal, httpRequest, request.projectPublicId(), "", "");
            NetworkInventoryUpdateDto update = monitoringService.discoverLocalNetwork(projectId);
            authorizedRangeService.recordNetworkEvent(
                "SCAN_COMPLETED", "OK", principal, httpRequest, request.projectPublicId(), "", "");
            if (update.changed()) {
                webSocketHandler.publish(request.projectPublicId(), update);
            }
            return update;
        } catch (com.hexora.network.service.NetworkScopeRejectedException exception) {
            authorizedRangeService.recordNetworkEvent(
                "SCAN_REJECTED", "DENEGADO", principal, httpRequest,
                request.projectPublicId(), "", exception.getMessage());
            throw exception;
        } catch (NetworkDiscoveryException exception) {
            authorizedRangeService.recordNetworkEvent(
                "DISCOVERY_FAILED", "ERROR", principal, httpRequest,
                request.projectPublicId(), "", exception.getMessage());
            throw exception;
        }
    }

    @GetMapping("/detected-devices")
    public List<NetworkDetectedDeviceDto> detectedDevices(
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.getDetectedDevices(projectId);
    }

    @GetMapping("/gateway")
    public ResponseEntity<NetworkDetectedDeviceDto> gateway(
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.findGateway(projectId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/discoveries/latest")
    public ResponseEntity<NetworkDiscoverySnapshotDto> latestDiscovery(
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        Optional<NetworkDiscoverySnapshotDto> snapshot = monitoringService.getLatestDiscoverySnapshot(projectId);
        return snapshot.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/discoveries/history")
    public List<NetworkDiscoverySnapshotDto> discoveryHistory(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.getDiscoveryHistory(limit, projectId);
    }

    @GetMapping("/status")
    public NetworkDashboardDto status(
        @RequestParam String projectPublicId,
        @AuthenticationPrincipal TinyHatPrincipal principal,
        HttpServletRequest httpRequest
    ) {
        logEndpoint("GET", httpRequest);
        long projectId = authorizedRangeService.requireProjectId(principal, projectPublicId);
        return monitoringService.getStatus(projectId);
    }

    private void logEndpoint(String expectedMethod, HttpServletRequest request) {
        LOGGER.info("Network endpoint {}: expected HTTP method {}, received {}",
            request.getRequestURI(), expectedMethod, request.getMethod());
    }
}
