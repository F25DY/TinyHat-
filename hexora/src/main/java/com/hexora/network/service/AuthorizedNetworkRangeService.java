package com.hexora.network.service;

import com.hexora.hexora.model.Auditoria;
import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.security.TinyHatPrincipal;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import com.hexora.hexora.service.PermissionService;
import com.hexora.network.model.AuthorizedNetworkRange;
import com.hexora.network.model.AuthorizedNetworkRangeDto;
import com.hexora.network.model.AuthorizedNetworkRangeRequest;
import com.hexora.network.model.NetworkProjectDto;
import com.hexora.network.repository.AuthorizedNetworkRangeRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizedNetworkRangeService {

    private final AuthorizedNetworkRangeRepository rangeRepository;
    private final PermissionService permissionService;
    private final CapabilityService capabilityService;
    private final NetworkMonitorProperties monitorProperties;
    private final com.hexora.hexora.security.TinyHatSecurityProperties securityProperties;
    private final AuditService auditService;
    private final Clock clock;
    private final NetworkScopeGuard scopeGuard;

    public AuthorizedNetworkRangeService(
        AuthorizedNetworkRangeRepository rangeRepository,
        PermissionService permissionService,
        CapabilityService capabilityService,
        NetworkMonitorProperties monitorProperties,
        com.hexora.hexora.security.TinyHatSecurityProperties securityProperties,
        AuditService auditService,
        Clock clock,
        NetworkScopeGuard scopeGuard
    ) {
        this.rangeRepository = rangeRepository;
        this.permissionService = permissionService;
        this.capabilityService = capabilityService;
        this.monitorProperties = monitorProperties;
        this.securityProperties = securityProperties;
        this.auditService = auditService;
        this.clock = clock;
        this.scopeGuard = scopeGuard;
    }

    @Transactional
    public AuthorizedNetworkRangeDto declare(
        TinyHatPrincipal principal,
        AuthorizedNetworkRangeRequest request,
        HttpServletRequest httpRequest
    ) {
        requireCapability(principal);
        if (request == null) {
            throw new IllegalArgumentException("Authorization range details are required");
        }
        Auditoria project = permissionService.requireProyecto(principal, request.projectPublicId());
        String reference = request.authorizationReference() == null
            ? ""
            : request.authorizationReference().trim();
        if (reference.isEmpty() || reference.length() > 255) {
            throw new IllegalArgumentException("Authorization reference must contain 1 to 255 characters");
        }

        Ipv4Cidr cidr = validateCidr(request.cidr());
        LocalDateTime declaredAt = LocalDateTime.now(clock);
        LocalDateTime validUntil = request.validUntil();
        if (validUntil != null && !validUntil.isAfter(declaredAt)) {
            throw new IllegalArgumentException("Authorization expiry must be in the future");
        }

        AuthorizedNetworkRange range = new AuthorizedNetworkRange();
        range.setCuentaId(principal.getCuentaId());
        range.setProyectoId(project.getIdAuditoria());
        range.setCidr(cidr.canonicalValue());
        range.setReferenciaAutorizacion(reference);
        range.setDeclaradoPor(principal.getUserId());
        range.setDeclaradoEn(declaredAt);
        range.setVigenteHasta(validUntil);
        range = rangeRepository.save(range);
        auditService.networkEvent(
            "RANGE_DECLARED",
            "OK",
            principal,
            httpRequest,
            project.getPublicId(),
            range.getCidr(),
            ""
        );
        return toDto(project.getPublicId(), range);
    }

    @Transactional(readOnly = true)
    public List<AuthorizedNetworkRangeDto> list(TinyHatPrincipal principal, String projectPublicId) {
        requireCapability(principal);
        Auditoria project = permissionService.requireProyecto(principal, projectPublicId);
        return rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(
                principal.getCuentaId(),
                project.getIdAuditoria())
            .stream()
            .map(range -> toDto(project.getPublicId(), range))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkProjectDto> visibleProjects(TinyHatPrincipal principal) {
        requireCapability(principal);
        return permissionService.proyectosVisibles(principal).stream()
            .map(project -> new NetworkProjectDto(project.getPublicId(), project.getNombre()))
            .toList();
    }

    @Transactional(readOnly = true)
    public long requireProjectId(TinyHatPrincipal principal, String projectPublicId) {
        requireCapability(principal);
        return permissionService.requireProyecto(principal, projectPublicId).getIdAuditoria();
    }

    @Transactional(readOnly = true)
    public void requireAuthorizedTarget(TinyHatPrincipal principal, long projectId, String address) {
        requireCapability(principal);
        scopeGuard.requireAuthorizedTarget(principal.getCuentaId(), projectId, address);
    }

    @Transactional(readOnly = true)
    public void requireAuthorizedInterface(TinyHatPrincipal principal, long projectId) {
        requireCapability(principal);
        scopeGuard.requireAuthorizedInterface(principal.getCuentaId(), projectId);
    }

    public void recordNetworkEvent(
        String eventName,
        String outcome,
        TinyHatPrincipal principal,
        HttpServletRequest request,
        String projectPublicId,
        String cidr,
        String reason
    ) {
        auditService.networkEvent(
            eventName,
            outcome,
            principal,
            request,
            projectPublicId,
            cidr,
            reason
        );
    }

    Ipv4Cidr validateCidr(String value) {
        Ipv4Cidr cidr = Ipv4Cidr.parse(value);
        if (cidr.prefixLength() < securityProperties.getNetwork().getMaxPrefix()) {
            throw new IllegalArgumentException(
                "Authorized network ranges may not be larger than /"
                    + securityProperties.getNetwork().getMaxPrefix()
            );
        }
        if (!cidr.isPrivateOrLocal() && !securityProperties.getNetwork().isAllowPublicRanges()) {
            throw new IllegalArgumentException("Public network ranges are disabled");
        }
        if (monitorProperties.getAllowedHosts().stream().anyMatch(host ->
            host.getAddress() != null && host.getAddress().equalsIgnoreCase(value))) {
            throw new IllegalArgumentException("CIDR must not be a hostname or configured host entry");
        }
        return cidr;
    }

    private void requireCapability(TinyHatPrincipal principal) {
        if (principal == null || !principal.isEnabled()) {
            throw new IllegalArgumentException("An active authenticated principal is required");
        }
        if (!capabilityService.isEnabled(principal.getCuentaId(), Capability.MODULO_RED)) {
            throw new NetworkCapabilityDisabledException();
        }
    }

    private AuthorizedNetworkRangeDto toDto(String projectPublicId, AuthorizedNetworkRange range) {
        return new AuthorizedNetworkRangeDto(
            projectPublicId,
            range.getCidr(),
            range.getReferenciaAutorizacion(),
            range.getDeclaradoEn(),
            range.getVigenteHasta()
        );
    }
}
