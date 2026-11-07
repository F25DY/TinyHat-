package com.hexora.network.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.hexora.network.model.NetworkHost;
import com.hexora.network.model.NetworkHostStatus;
import com.hexora.network.model.NetworkDetectedDevice;
import com.hexora.network.model.NetworkDiscoverySnapshot;
import com.hexora.network.model.NetworkDiscoverySnapshotDevice;
import com.hexora.network.model.MonitoredPort;
import com.hexora.network.model.NetworkScan;
import com.hexora.network.repository.MonitoredPortRepository;
import com.hexora.network.repository.NetworkDetectedDeviceRepository;
import com.hexora.network.repository.NetworkDiscoveryEventRepository;
import com.hexora.network.repository.NetworkDiscoverySnapshotRepository;
import com.hexora.network.repository.NetworkEventRepository;
import com.hexora.network.repository.NetworkHostRepository;
import com.hexora.network.repository.NetworkScanRepository;
import com.hexora.network.repository.PortScanResultRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkMonitoringServiceTest {

    @Mock
    private NetworkHostRepository hostRepository;

    @Mock
    private NetworkScanRepository scanRepository;

    @Mock
    private PortScanResultRepository portResultRepository;

    @Mock
    private NetworkEventRepository eventRepository;

    @Mock
    private NetworkDetectedDeviceRepository detectedDeviceRepository;

    @Mock
    private NetworkDiscoveryEventRepository discoveryEventRepository;

    @Mock
    private NetworkDiscoverySnapshotRepository discoverySnapshotRepository;

    @Mock
    private LocalNetworkDiscoveryService localNetworkDiscoveryService;

    @Mock
    private MonitoredPortRepository monitoredPortRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ThreadPoolTaskExecutor portProbeExecutor;

    private NetworkMonitorProperties properties;
    private NetworkMonitoringService service;

    @BeforeEach
    void setUp() {
        properties = new NetworkMonitorProperties();
        service = new NetworkMonitoringService(
            hostRepository,
            scanRepository,
            portResultRepository,
            eventRepository,
            detectedDeviceRepository,
            discoveryEventRepository,
            discoverySnapshotRepository,
            monitoredPortRepository,
            properties,
            localNetworkDiscoveryService,
            transactionManager,
            portProbeExecutor
        );
    }

    @Test
    void marksAnArpDiscoveredDeviceOfflineOnTheFirstMissingScan() {
        NetworkDetectedDevice device = new NetworkDetectedDevice();
        device.setAddress("192.168.1.25");
        device.setMacAddress("00:11:22:33:44:55");
        device.setHostName("NAS");
        device.setSubnet("192.168.1.0/24");
        device.setStatus(NetworkHostStatus.ONLINE);
        device.setFirstDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        device.setLastDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        device.setLastStatusChangedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));

        when(localNetworkDiscoveryService.readCurrentNeighbors()).thenReturn(new LocalNetworkSnapshot(
            "192.168.1.0/24",
            "ARP",
            Instant.parse("2026-10-27T12:01:00Z"),
            List.of()
        ));
        when(hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of());
        when(detectedDeviceRepository.findAll()).thenReturn(List.of(device));
        when(detectedDeviceRepository.findAllByOrderByLastDetectedAtDesc()).thenReturn(List.of(device));

        service.synchronizeDetectedDevices();

        assertEquals(NetworkHostStatus.OFFLINE, device.getStatus());
        assertEquals(1, device.getConsecutiveMisses());
        verify(discoveryEventRepository, times(1)).save(any());
    }

    @Test
    void savesEveryManualDiscoveryAsAHistorySnapshot() {
        when(localNetworkDiscoveryService.readCurrentNeighbors()).thenReturn(new LocalNetworkSnapshot(
            "192.168.1.0/24",
            "ARP",
            Instant.parse("2026-10-27T12:01:00Z"),
            List.of()
        ));
        when(hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of());
        when(detectedDeviceRepository.findAll()).thenReturn(List.of());

        service.discoverLocalNetwork();

        ArgumentCaptor<NetworkDiscoverySnapshot> snapshotCaptor =
            ArgumentCaptor.forClass(NetworkDiscoverySnapshot.class);
        verify(discoverySnapshotRepository).save(snapshotCaptor.capture());
        assertEquals("192.168.1.0/24", snapshotCaptor.getValue().getSubnet());
        assertEquals(Instant.parse("2026-10-27T12:01:00Z"),
            snapshotCaptor.getValue().getObservedAt().toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    void showsOnlyDevicesSeenInTheCurrentSubnetAndKeepsOldDevicesOutOfTheSnapshot() {
        NetworkDetectedDevice staleDevice = new NetworkDetectedDevice();
        staleDevice.setAddress("172.20.10.2");
        staleDevice.setHostName("old-network-device");
        staleDevice.setSubnet("172.20.10.0/28");
        staleDevice.setStatus(NetworkHostStatus.ONLINE);
        staleDevice.setFirstDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        staleDevice.setLastDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        staleDevice.setLastStatusChangedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        List<NetworkDetectedDevice> knownDevices = new java.util.ArrayList<>(List.of(staleDevice));

        NetworkHost registeredHost = new NetworkHost();
        registeredHost.setAddress("192.168.1.25");
        registeredHost.setDisplayName("Current phone");
        when(localNetworkDiscoveryService.readCurrentNeighbors()).thenReturn(new LocalNetworkSnapshot(
            "192.168.1.0/24",
            "ARP",
            Instant.parse("2026-10-27T12:01:00Z"),
            List.of(new NeighborEntry("192.168.1.25", "00:11:22:33:44:55", "ICMP + ARP"))
        ));
        when(hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(registeredHost));
        when(detectedDeviceRepository.findByAddressIgnoreCase("192.168.1.25")).thenReturn(Optional.empty());
        when(detectedDeviceRepository.save(any())).thenAnswer(invocation -> {
            NetworkDetectedDevice saved = invocation.getArgument(0);
            knownDevices.removeIf(device -> device.getAddress().equalsIgnoreCase(saved.getAddress()));
            knownDevices.add(saved);
            return saved;
        });
        when(detectedDeviceRepository.findAll()).thenAnswer(invocation -> new java.util.ArrayList<>(knownDevices));
        when(detectedDeviceRepository.findAllByOrderByLastDetectedAtDesc()).thenReturn(knownDevices);

        var update = service.discoverLocalNetwork();

        assertEquals(1, update.devices().size());
        assertEquals("192.168.1.25", update.devices().getFirst().address());
        assertEquals(NetworkHostStatus.OFFLINE, staleDevice.getStatus());
        ArgumentCaptor<NetworkDiscoverySnapshot> snapshotCaptor =
            ArgumentCaptor.forClass(NetworkDiscoverySnapshot.class);
        verify(discoverySnapshotRepository).save(snapshotCaptor.capture());
        List<NetworkDiscoverySnapshotDevice> savedDevices = snapshotCaptor.getValue().getDevices();
        assertEquals(1, savedDevices.size());
        assertEquals("192.168.1.25", savedDevices.getFirst().getAddress());
    }

    @Test
    void latestDetectionHidesOfflineAndOutOfSubnetEntriesFromOlderSnapshots() {
        var snapshot = new NetworkDiscoverySnapshot();
        snapshot.setSubnet("192.168.1.0/24");
        snapshot.setSource("ARP");
        snapshot.setObservedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        snapshot.addDevice(snapshotDevice("192.168.1.1", NetworkHostStatus.ONLINE));
        snapshot.addDevice(snapshotDevice("192.168.1.12", NetworkHostStatus.OFFLINE));
        snapshot.addDevice(snapshotDevice("172.20.10.2", NetworkHostStatus.ONLINE));
        when(discoverySnapshotRepository.findFirstByOrderByObservedAtDesc()).thenReturn(Optional.of(snapshot));

        var latest = service.getLatestDiscoverySnapshot().orElseThrow();

        assertEquals(1, latest.devices().size());
        assertEquals("192.168.1.1", latest.devices().getFirst().address());
    }

    private NetworkDiscoverySnapshotDevice snapshotDevice(String address, NetworkHostStatus status) {
        var device = new NetworkDiscoverySnapshotDevice();
        device.setAddress(address);
        device.setHostName(address);
        device.setDetectionMethod("ARP");
        device.setStatus(status);
        device.setFirstDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 11, 0));
        device.setLastDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));
        return device;
    }

    @Test
    void keepsAResponsiveHostOnlineAndResetsItsFailureCount() {
        var state = NetworkMonitoringService.nextHostProbeState(NetworkHostStatus.OFFLINE, 2, true);

        assertEquals(NetworkHostStatus.ONLINE, state.status());
        assertEquals(0, state.consecutiveFailures());
    }

    @Test
    void waitsForTwoConsecutiveFailuresBeforeMarkingAHostOffline() {
        var firstFailure = NetworkMonitoringService.nextHostProbeState(NetworkHostStatus.ONLINE, 0, false);
        var secondFailure = NetworkMonitoringService.nextHostProbeState(NetworkHostStatus.ONLINE, 1, false);

        assertEquals(NetworkHostStatus.ONLINE, firstFailure.status());
        assertEquals(1, firstFailure.consecutiveFailures());
        assertEquals(NetworkHostStatus.OFFLINE, secondFailure.status());
        assertEquals(2, secondFailure.consecutiveFailures());
    }

    @Test
    void keepsAnUnknownHostUnknownAfterItsFirstFailedCheck() {
        var failedCheck = NetworkMonitoringService.nextHostProbeState(NetworkHostStatus.UNKNOWN, 0, false);

        assertEquals(NetworkHostStatus.UNKNOWN, failedCheck.status());
        assertEquals(1, failedCheck.consecutiveFailures());
    }

    @Test
    void rejectsRangesLargerThanTheConfiguredLimitBeforeLoadingHost() {
        assertThrows(IllegalArgumentException.class, () -> service.scanHost(7L, 1, 129));

        verifyNoInteractions(hostRepository, scanRepository, portResultRepository, eventRepository, monitoredPortRepository);
    }

    @Test
    void refusesHostsThatAreNotInTheOperatorAllowlist() {
        NetworkHost host = new NetworkHost();
        host.setId(7L);
        host.setDisplayName("Unlisted host");
        host.setAddress("192.0.2.25");
        when(hostRepository.findById(7L)).thenReturn(Optional.of(host));

        assertThrows(EntityNotFoundException.class, () -> service.scanHost(7L, null, null));

        verify(hostRepository).findById(7L);
        verifyNoInteractions(scanRepository, portResultRepository, eventRepository, monitoredPortRepository);
    }

    @Test
    void registersPrivateLanDevicesWithTheirConfiguredPorts() {
        when(hostRepository.findByAddressIgnoreCase("192.168.1.25")).thenReturn(Optional.empty());
        when(hostRepository.save(any(NetworkHost.class))).thenAnswer((Answer<NetworkHost>) invocation -> {
            NetworkHost host = invocation.getArgument(0);
            host.setId(10L);
            return host;
        });

        var result = service.registerHost(" NAS ", "192.168.1.25", List.of(22, 443));

        assertEquals("NAS", result.name());
        assertEquals("192.168.1.25", result.address());
        assertEquals(NetworkHostStatus.OFFLINE, result.status());
        org.junit.jupiter.api.Assertions.assertTrue(result.managedByUi());
        verify(monitoredPortRepository).saveAll(any());
    }

    @Test
    void rejectsPublicAddressesBeforePersistingADevice() {
        assertThrows(IllegalArgumentException.class, () ->
            service.registerHost("External host", "8.8.8.8", List.of(443)));

        verifyNoInteractions(hostRepository, scanRepository, portResultRepository, eventRepository, monitoredPortRepository);
    }

    @Test
    void rejectsMoreThanThirtyTwoConfiguredPorts() {
        List<Integer> ports = java.util.stream.IntStream.rangeClosed(1, 33).boxed().toList();

        assertThrows(IllegalArgumentException.class, () ->
            service.registerHost("Workstation", "192.168.1.25", ports));

        verifyNoInteractions(hostRepository, scanRepository, portResultRepository, eventRepository, monitoredPortRepository);
    }

    @Test
    void addsAConfiguredPortToAnAuthorizedDevice() {
        NetworkHost host = new NetworkHost();
        host.setId(7L);
        host.setAddress("192.168.1.25");
        host.setDisplayName("NAS");
        host.setManagedByUi(true);
        when(hostRepository.findById(7L)).thenReturn(Optional.of(host));
        when(monitoredPortRepository.existsByHost_IdAndPort(7L, 8080)).thenReturn(false);
        when(monitoredPortRepository.countByHost_Id(7L)).thenReturn(1L);
        when(monitoredPortRepository.save(any(MonitoredPort.class))).thenAnswer(invocation ->
            invocation.getArgument(0));

        var result = service.addMonitoredPort(7L, 8080);

        assertEquals(8080, result.port());
        assertEquals("Desconocido", result.service());
        org.junit.jupiter.api.Assertions.assertTrue(result.enabled());
    }

    @Test
    void rejectsPortsOutsideTcpPortRange() {
        assertThrows(IllegalArgumentException.class, () -> service.addMonitoredPort(7L, 65536));

        verifyNoInteractions(hostRepository, monitoredPortRepository);
    }

    @Test
    void canPauseAnIndividualMonitoredPortWithoutRemovingIt() {
        NetworkHost host = new NetworkHost();
        host.setId(7L);
        host.setAddress("192.168.1.25");
        host.setDisplayName("NAS");
        host.setManagedByUi(true);
        MonitoredPort monitoredPort = new MonitoredPort();
        monitoredPort.setHost(host);
        monitoredPort.setPort(443);
        monitoredPort.setEnabled(true);
        when(hostRepository.findById(7L)).thenReturn(Optional.of(host));
        when(monitoredPortRepository.findByHost_IdAndPort(7L, 443)).thenReturn(Optional.of(monitoredPort));
        when(monitoredPortRepository.save(monitoredPort)).thenReturn(monitoredPort);

        var result = service.setMonitoredPortEnabled(7L, 443, false);

        assertFalse(result.enabled());
        verify(monitoredPortRepository).save(monitoredPort);
    }

    @Test
    void clearsHistoryAndRetainsHostsWithUnknownStatus() {
        NetworkHost host = new NetworkHost();
        host.setId(7L);
        host.setStatus(NetworkHostStatus.ONLINE);
        host.setLatencyMs(12);
        when(hostRepository.findAll()).thenReturn(List.of(host));

        service.clearHistory();

        verify(discoveryEventRepository).deleteAllInBatch();
        verify(discoverySnapshotRepository).deleteAllInBatch();
        verify(eventRepository).deleteAllInBatch();
        verify(portResultRepository).deleteAllInBatch();
        verify(scanRepository).deleteAllInBatch();
        verify(hostRepository).saveAll(List.of(host));
        assertEquals(NetworkHostStatus.UNKNOWN, host.getStatus());
        assertNull(host.getLatencyMs());
    }

    @Test
    void purgesExpiredHistoryInForeignKeySafeBatches() {
        List<Long> expiredScanIds = List.of(12L, 13L);
        when(scanRepository.findIdsStartedBefore(any(), any())).thenReturn(expiredScanIds);
        when(eventRepository.findUnlinkedIdsBefore(any(), any())).thenReturn(List.of(21L, 22L, 23L));
        when(discoveryEventRepository.findIdsCreatedBefore(any(), any())).thenReturn(List.of(31L, 32L, 33L, 34L));
        when(discoverySnapshotRepository.findIdsObservedBefore(any(), any())).thenReturn(List.of(41L, 42L, 43L, 44L, 45L));

        int deletedRecords = service.purgeExpiredHistory();

        assertEquals(14, deletedRecords);
        verify(eventRepository).deleteAllForScans(expiredScanIds);
        verify(portResultRepository).deleteAllForScans(expiredScanIds);
        verify(scanRepository).deleteAllByIdInBatch(expiredScanIds);
        verify(eventRepository).deleteAllByIdInBatch(List.of(21L, 22L, 23L));
        verify(discoveryEventRepository).deleteAllByIdInBatch(List.of(31L, 32L, 33L, 34L));
        verify(discoverySnapshotRepository).deleteAllByIdInBatch(List.of(41L, 42L, 43L, 44L, 45L));
    }

    @Test
    void rejectsHistoryRetentionOutsideTheSupportedRange() {
        properties.setHistoryRetentionDays(0);

        assertThrows(IllegalStateException.class, () -> new NetworkMonitoringService(
            hostRepository,
            scanRepository,
            portResultRepository,
            eventRepository,
            detectedDeviceRepository,
            discoveryEventRepository,
            discoverySnapshotRepository,
            monitoredPortRepository,
            properties,
            localNetworkDiscoveryService,
            transactionManager,
            portProbeExecutor
        ));
    }

    @Test
    void clearsOnlyAUiManagedHostsHistory() {
        NetworkHost host = new NetworkHost();
        host.setId(7L);
        host.setManagedByUi(true);
        host.setStatus(NetworkHostStatus.ONLINE);
        when(hostRepository.findById(7L)).thenReturn(Optional.of(host));

        service.clearHostHistory(7L);

        verify(eventRepository).deleteAllForHost(7L);
        verify(portResultRepository).deleteAllForHost(7L);
        verify(scanRepository).deleteAllForHost(7L);
        verify(hostRepository).save(host);
        assertEquals(NetworkHostStatus.UNKNOWN, host.getStatus());
    }

    @Test
    void rejectsInvalidSelectedHistoryIdsBeforeDeletingAnything() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteSelectedScans(List.of(0L)));

        verifyNoInteractions(scanRepository, portResultRepository, eventRepository);
    }

    @Test
    void deletesSelectedScanResultsAndResetsHostWhenNoHistoryRemains() {
        NetworkHost host = new NetworkHost();
        host.setId(7L);
        host.setManagedByUi(true);
        host.setStatus(NetworkHostStatus.ONLINE);
        NetworkScan scan = new NetworkScan();
        scan.setId(12L);
        scan.setHost(host);
        when(scanRepository.findAllById(List.of(12L))).thenReturn(List.of(scan));
        when(hostRepository.findById(7L)).thenReturn(Optional.of(host));
        when(scanRepository.findFirstByHost_IdOrderByStartedAtDesc(7L)).thenReturn(Optional.empty());

        service.deleteSelectedScans(List.of(12L));

        verify(eventRepository).deleteAllForScans(List.of(12L));
        verify(portResultRepository).deleteAllForScans(List.of(12L));
        verify(scanRepository).deleteAllByIdInBatch(List.of(12L));
        verify(hostRepository).save(host);
        assertEquals(NetworkHostStatus.UNKNOWN, host.getStatus());
    }

    @Test
    void findGatewayDiscoversAndStoresGatewayWhenActiveGatewayIsFound() {
        when(localNetworkDiscoveryService.findGatewayAddress()).thenReturn(Optional.of("192.168.1.1"));
        when(detectedDeviceRepository.findByAddressIgnoreCase("192.168.1.1")).thenReturn(Optional.empty());
        when(hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of());
        when(detectedDeviceRepository.save(any(NetworkDetectedDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var gatewayOpt = service.findGateway();

        assertTrue(gatewayOpt.isPresent());
        var gateway = gatewayOpt.get();
        assertEquals("192.168.1.1", gateway.address());
        assertTrue(gateway.gateway());
        assertEquals("192.168.1.1", gateway.gatewayAddress());
        assertEquals(NetworkHostStatus.ONLINE, gateway.status());
        assertTrue(gateway.detectionMethod().contains("Puerta de enlace"));
    }

    @Test
    void findGatewayFallsBackToStoredGatewayWhenNoActiveGatewayResolved() {
        NetworkDetectedDevice storedGateway = new NetworkDetectedDevice();
        storedGateway.setAddress("192.168.1.254");
        storedGateway.setHostName("stored-router");
        storedGateway.setGateway(true);
        storedGateway.setGatewayAddress("192.168.1.254");
        storedGateway.setDetectionMethod("Puerta de enlace");
        storedGateway.setStatus(NetworkHostStatus.ONLINE);
        storedGateway.setFirstDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 10, 0));
        storedGateway.setLastDetectedAt(java.time.LocalDateTime.of(2026, 10, 27, 12, 0));

        when(localNetworkDiscoveryService.findGatewayAddress()).thenReturn(Optional.empty());
        when(detectedDeviceRepository.findFirstByGatewayTrueOrderByLastDetectedAtDesc()).thenReturn(Optional.of(storedGateway));
        when(hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of());

        var gatewayOpt = service.findGateway();

        assertTrue(gatewayOpt.isPresent());
        var gateway = gatewayOpt.get();
        assertEquals("192.168.1.254", gateway.address());
        assertTrue(gateway.gateway());
        assertEquals("192.168.1.254", gateway.gatewayAddress());
    }
}
