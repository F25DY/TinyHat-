package com.hexora.network.service;

import com.hexora.network.model.AuthorizedNetworkRange;
import com.hexora.network.repository.AuthorizedNetworkRangeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class NetworkScopeGuard {

    private final AuthorizedNetworkRangeRepository rangeRepository;
    private final LocalNetworkDiscoveryService localNetworkDiscoveryService;
    private final TinyHatNetworkPolicy policy;
    private final Clock clock;

    public NetworkScopeGuard(
        AuthorizedNetworkRangeRepository rangeRepository,
        LocalNetworkDiscoveryService localNetworkDiscoveryService,
        TinyHatNetworkPolicy policy,
        Clock clock
    ) {
        this.rangeRepository = rangeRepository;
        this.localNetworkDiscoveryService = localNetworkDiscoveryService;
        this.policy = policy;
        this.clock = clock;
    }

    public void requireAuthorizedTarget(long accountId, Long projectId, String targetAddress) {
        if (accountId < 1 || projectId == null || projectId < 1) {
            throw new NetworkScopeRejectedException("A project with an authorized range is required");
        }
        Ipv4Cidr target;
        try {
            target = new Ipv4Cidr(Ipv4Cidr.parseAddress(targetAddress), 32);
        } catch (IllegalArgumentException | NetworkDiscoveryException exception) {
            throw new NetworkScopeRejectedException("The target address must be a literal IPv4 address", exception);
        }
        var ranges = validRanges(accountId, projectId);
        if (ranges.isEmpty()) {
            throw new NetworkScopeRejectedException("No current authorization exists for this project");
        }
        Ipv4Cidr activeInterface = activeInterface();
        boolean authorized = ranges.stream()
            .anyMatch(range -> containsAuthorizedScope(range, activeInterface, target.network()));
        if (!authorized) {
            throw new NetworkScopeRejectedException(
                "The active interface and target must be inside a currently authorized project range");
        }
    }

    public Ipv4Cidr requireAuthorizedInterface(long accountId, Long projectId) {
        if (accountId < 1 || projectId == null || projectId < 1) {
            throw new NetworkScopeRejectedException("A project with an authorized range is required");
        }
        var ranges = validRanges(accountId, projectId);
        if (ranges.isEmpty()) {
            throw new NetworkScopeRejectedException("No current authorization exists for this project");
        }
        Ipv4Cidr activeInterface = activeInterface();
        boolean authorized = ranges.stream().anyMatch(range -> containsAuthorizedInterface(range, activeInterface));
        if (!authorized) {
            throw new NetworkScopeRejectedException(
                "The active interface must be contained in a currently authorized project range");
        }
        return activeInterface;
    }

    public boolean isAuthorizedAddress(long accountId, Long projectId, String address) {
        if (accountId < 1 || projectId == null || projectId < 1) {
            return false;
        }
        final long parsedAddress;
        try {
            parsedAddress = Ipv4Cidr.parseAddress(address);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return validRanges(accountId, projectId).stream()
            .anyMatch(range -> containsAuthorizedAddress(range, parsedAddress));
    }

    public boolean isAuthorizedSubnet(long accountId, Long projectId, String subnet) {
        if (accountId < 1 || projectId == null || projectId < 1) {
            return false;
        }
        final Ipv4Cidr parsedSubnet;
        try {
            parsedSubnet = Ipv4Cidr.parse(subnet);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        return validRanges(accountId, projectId).stream()
            .anyMatch(range -> containsAuthorizedSubnet(range, parsedSubnet));
    }

    private java.util.List<AuthorizedNetworkRange> validRanges(long accountId, Long projectId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(accountId, projectId)
            .stream()
            .filter(range -> range.getVigenteHasta() == null || range.getVigenteHasta().isAfter(now))
            .toList();
    }

    private Ipv4Cidr activeInterface() {
        try {
            return Ipv4Cidr.parse(localNetworkDiscoveryService.activeInterfaceCidr());
        } catch (IllegalArgumentException | NetworkDiscoveryException exception) {
            throw new NetworkScopeRejectedException("The active network interface could not be validated", exception);
        }
    }

    private boolean containsAuthorizedInterface(AuthorizedNetworkRange range, Ipv4Cidr activeInterface) {
        try {
            Ipv4Cidr authorizedRange = Ipv4Cidr.parse(range.getCidr());
            return isAllowedRange(authorizedRange) && authorizedRange.contains(activeInterface);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean containsAuthorizedAddress(AuthorizedNetworkRange range, long address) {
        try {
            Ipv4Cidr authorizedRange = Ipv4Cidr.parse(range.getCidr());
            return isAllowedRange(authorizedRange) && authorizedRange.containsAddress(address);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean containsAuthorizedSubnet(AuthorizedNetworkRange range, Ipv4Cidr subnet) {
        try {
            Ipv4Cidr authorizedRange = Ipv4Cidr.parse(range.getCidr());
            return isAllowedRange(authorizedRange) && authorizedRange.contains(subnet);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean containsAuthorizedScope(
        AuthorizedNetworkRange range,
        Ipv4Cidr activeInterface,
        long targetAddress
    ) {
        try {
            Ipv4Cidr authorizedRange = Ipv4Cidr.parse(range.getCidr());
            return isAllowedRange(authorizedRange)
                && authorizedRange.contains(activeInterface)
                && authorizedRange.containsAddress(targetAddress);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isAllowedRange(Ipv4Cidr cidr) {
        return cidr.prefixLength() >= policy.maxPrefix()
            && (policy.allowPublicRanges() || cidr.isPrivateOrLocal());
    }
}
