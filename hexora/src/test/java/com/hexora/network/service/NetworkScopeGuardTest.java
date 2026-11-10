package com.hexora.network.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexora.network.model.AuthorizedNetworkRange;
import com.hexora.network.repository.AuthorizedNetworkRangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkScopeGuardTest {

    private final AuthorizedNetworkRangeRepository rangeRepository = mock(AuthorizedNetworkRangeRepository.class);
    private final LocalNetworkDiscoveryService localNetwork = mock(LocalNetworkDiscoveryService.class);
    private final TinyHatNetworkPolicy policy = mock(TinyHatNetworkPolicy.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
    private final NetworkScopeGuard guard =
        new NetworkScopeGuard(rangeRepository, localNetwork, policy, clock);

    @BeforeEach
    void setUp() {
        when(localNetwork.activeInterfaceCidr()).thenReturn("192.168.10.0/24");
        when(policy.maxPrefix()).thenReturn(24);
        when(policy.allowPublicRanges()).thenReturn(false);
    }

    @Test
    void permitsOnlyTargetsInsideAValidAuthorizedProjectRangeAndActiveInterface() {
        when(rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(4L, 9L))
            .thenReturn(List.of(range("192.168.10.0/24", null)));

        guard.requireAuthorizedTarget(4L, 9L, "192.168.10.25");

        verify(localNetwork).activeInterfaceCidr();
    }

    @Test
    void rejectsTargetsOutsideTheAuthorizedRange() {
        when(rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(4L, 9L))
            .thenReturn(List.of(range("192.168.10.0/24", null)));

        assertThatThrownBy(() -> guard.requireAuthorizedTarget(4L, 9L, "192.168.11.25"))
            .isInstanceOf(NetworkScopeRejectedException.class);
    }

    @Test
    void rejectsMissingOrExpiredAuthorizationBeforeAnyDiscoveryOrScannerCanRun() {
        when(rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(4L, 9L))
            .thenReturn(List.of(range("192.168.10.0/24", LocalDateTime.of(2026, 11, 9, 11, 59))));

        assertThatThrownBy(() -> guard.requireAuthorizedTarget(4L, 9L, "192.168.10.25"))
            .isInstanceOf(NetworkScopeRejectedException.class);

        verifyNoInteractions(localNetwork);
    }

    @Test
    void rejectsHostnamesAndShellMetacharactersWithoutResolvingThem() {
        assertThatThrownBy(() -> guard.requireAuthorizedTarget(4L, 9L, "10.0.0.1; calc"))
            .isInstanceOf(NetworkScopeRejectedException.class);

        verifyNoInteractions(localNetwork, rangeRepository);
    }

    @Test
    void rejectsAnActiveInterfaceOutsideTheProjectRange() {
        when(localNetwork.activeInterfaceCidr()).thenReturn("192.168.11.0/24");
        when(rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(4L, 9L))
            .thenReturn(List.of(range("192.168.10.0/24", null)));

        assertThatThrownBy(() -> guard.requireAuthorizedInterface(4L, 9L))
            .isInstanceOf(NetworkScopeRejectedException.class);
    }

    @Test
    void filtersReadResultsByLiveProjectAddressAndSubnetScope() {
        when(rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(4L, 9L))
            .thenReturn(List.of(range("192.168.10.0/24", null)));

        org.assertj.core.api.Assertions.assertThat(guard.isAuthorizedAddress(4L, 9L, "192.168.10.25")).isTrue();
        org.assertj.core.api.Assertions.assertThat(guard.isAuthorizedAddress(4L, 9L, "192.168.11.25")).isFalse();
        org.assertj.core.api.Assertions.assertThat(guard.isAuthorizedSubnet(4L, 9L, "192.168.10.0/24")).isTrue();
        org.assertj.core.api.Assertions.assertThat(guard.isAuthorizedSubnet(4L, 9L, "192.168.0.0/16")).isFalse();

        when(rangeRepository.findAllByCuentaIdAndProyectoIdOrderByDeclaradoEnDesc(4L, 9L))
            .thenReturn(List.of(range("192.168.10.0/24", LocalDateTime.of(2026, 11, 9, 11, 59))));
        org.assertj.core.api.Assertions.assertThat(guard.isAuthorizedAddress(4L, 9L, "192.168.10.25")).isFalse();
    }

    private AuthorizedNetworkRange range(String cidr, LocalDateTime validUntil) {
        AuthorizedNetworkRange range = new AuthorizedNetworkRange();
        range.setCidr(cidr);
        range.setVigenteHasta(validUntil);
        return range;
    }
}
