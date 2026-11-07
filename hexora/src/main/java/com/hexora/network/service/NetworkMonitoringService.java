package com.hexora.network.service;

import com.hexora.network.model.NetworkDashboardDto;
import com.hexora.network.model.NetworkDetectedDevice;
import com.hexora.network.model.NetworkDetectedDeviceDto;
import com.hexora.network.model.NetworkDiscoveryEvent;
import com.hexora.network.model.NetworkDiscoverySnapshot;
import com.hexora.network.model.NetworkDiscoverySnapshotDevice;
import com.hexora.network.model.NetworkDiscoverySnapshotDto;
import com.hexora.network.model.NetworkEventType;
import com.hexora.network.model.NetworkEvent;
import com.hexora.network.model.NetworkEventDto;
import com.hexora.network.model.NetworkHost;
import com.hexora.network.model.NetworkHostDto;
import com.hexora.network.model.NetworkHostStatus;
import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.model.MonitoredPort;
import com.hexora.network.model.MonitoredPortDto;
import com.hexora.network.model.NetworkScan;
import com.hexora.network.model.NetworkScanDto;
import com.hexora.network.model.NetworkScanStatus;
import com.hexora.network.model.PortScanResult;
import com.hexora.network.model.PortScanResultDto;
import com.hexora.network.model.PortStatus;
import com.hexora.network.repository.NetworkEventRepository;
import com.hexora.network.repository.NetworkDetectedDeviceRepository;
import com.hexora.network.repository.NetworkDiscoveryEventRepository;
import com.hexora.network.repository.NetworkDiscoverySnapshotRepository;
import com.hexora.network.repository.NetworkHostRepository;
import com.hexora.network.repository.NetworkScanRepository;
import com.hexora.network.repository.MonitoredPortRepository;
import com.hexora.network.repository.PortScanResultRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NetworkMonitoringService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitoringService.class);
    private static final int MAX_RANGE_SIZE = 128;
    private static final int MAX_CONFIGURED_PORTS = 32;
    private static final int MAX_DISCOVERY_HISTORY = 50;
    private static final int HISTORY_CLEANUP_BATCH_SIZE = 500;
    private static final int HOST_FAILURES_BEFORE_OFFLINE = 2;
    private static final long PORT_SCAN_TIMEOUT_SECONDS = 30;
    private static final long HOSTNAME_LOOKUP_TIMEOUT_MILLIS = 500;
    private static final ExecutorService HOSTNAME_LOOKUP_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "network-hostname-lookup");
        thread.setDaemon(true);
        return thread;
    });
    private final NetworkHostRepository hostRepository;
    private final NetworkScanRepository scanRepository;
    private final PortScanResultRepository portResultRepository;
    private final NetworkEventRepository eventRepository;
    private final NetworkDetectedDeviceRepository detectedDeviceRepository;
    private final NetworkDiscoveryEventRepository discoveryEventRepository;
    private final NetworkDiscoverySnapshotRepository discoverySnapshotRepository;
    private final MonitoredPortRepository monitoredPortRepository;
    private final NetworkMonitorProperties properties;
    private final NetworkReachabilityProbe reachabilityProbe = new NetworkReachabilityProbe();
    private final LocalNetworkDiscoveryService localNetworkDiscoveryService;
    private final TransactionTemplate transactionTemplate;
    private final ThreadPoolTaskExecutor portProbeExecutor;
    private final ConcurrentMap<Long, Long> nextManualScanAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ReentrantLock> scanLocks = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock historyLock = new ReentrantReadWriteLock(true);

    public NetworkMonitoringService(
        NetworkHostRepository hostRepository,
        NetworkScanRepository scanRepository,
        PortScanResultRepository portResultRepository,
        NetworkEventRepository eventRepository,
        NetworkDetectedDeviceRepository detectedDeviceRepository,
        NetworkDiscoveryEventRepository discoveryEventRepository,
        NetworkDiscoverySnapshotRepository discoverySnapshotRepository,
        MonitoredPortRepository monitoredPortRepository,
        NetworkMonitorProperties properties,
        LocalNetworkDiscoveryService localNetworkDiscoveryService,
        PlatformTransactionManager transactionManager,
        @Qualifier("networkPortProbeExecutor") ThreadPoolTaskExecutor portProbeExecutor
    ) {
        this.hostRepository = hostRepository;
        this.scanRepository = scanRepository;
        this.portResultRepository = portResultRepository;
        this.eventRepository = eventRepository;
        this.detectedDeviceRepository = detectedDeviceRepository;
        this.discoveryEventRepository = discoveryEventRepository;
        this.discoverySnapshotRepository = discoverySnapshotRepository;
        this.monitoredPortRepository = monitoredPortRepository;
        this.properties = properties;
        this.localNetworkDiscoveryService = localNetworkDiscoveryService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.portProbeExecutor = portProbeExecutor;
        validateConfiguration();
    }

    static HostProbeState nextHostProbeState(
        NetworkHostStatus previousStatus,
        int previousFailures,
        boolean responsive
    ) {
        int consecutiveFailures = responsive ? 0 : previousFailures + 1;
        NetworkHostStatus status = responsive
            ? NetworkHostStatus.ONLINE
            : consecutiveFailures >= HOST_FAILURES_BEFORE_OFFLINE
                ? NetworkHostStatus.OFFLINE
                : previousStatus;
        return new HostProbeState(status, consecutiveFailures);
    }

    record HostProbeState(NetworkHostStatus status, int consecutiveFailures) {
    }

    @Transactional
    public NetworkInventoryUpdateDto discoverLocalNetwork() {
        historyLock.readLock().lock();
        try {
            NetworkInventoryUpdateDto update = synchronizeDetectedDevices();
            saveDiscoverySnapshot(update);
            return update;
        } finally {
            historyLock.readLock().unlock();
        }
    }

    @Transactional(readOnly = true)
    public List<NetworkDetectedDeviceDto> getDetectedDevices() {
        return mapDetectedDevices(detectedDeviceRepository.findAllByOrderByLastDetectedAtDesc());
    }

    @Transactional(readOnly = true)
    public Optional<NetworkDiscoverySnapshotDto> getLatestDiscoverySnapshot() {
        return discoverySnapshotRepository.findFirstByOrderByObservedAtDesc()
            .map(snapshot -> {
                NetworkDiscoverySnapshotDto latest = toSnapshotDto(snapshot);
                return new NetworkDiscoverySnapshotDto(
                    latest.id(),
                    latest.subnet(),
                    latest.source(),
                    latest.observedAt(),
                    latest.devices().stream()
                        .filter(device -> device.status() == NetworkHostStatus.ONLINE)
                        .filter(device -> isAddressInSubnet(device.address(), latest.subnet()))
                        .toList()
                );
            });
    }

    private boolean isAddressInSubnet(String address, String subnet) {
        String[] parts = subnet.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalStateException("Discovered subnet is missing its prefix length");
        }
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Discovered subnet has an invalid prefix length", exception);
        }
        if (prefixLength < 0 || prefixLength > 32) {
            throw new IllegalStateException("Discovered subnet has an invalid prefix length");
        }
        long mask = prefixLength == 0 ? 0 : (0xFFFF_FFFFL << (32 - prefixLength)) & 0xFFFF_FFFFL;
        return (ipv4ToLong(address) & mask) == (ipv4ToLong(parts[0]) & mask);
    }

    private long ipv4ToLong(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            throw new IllegalStateException("Discovered address is not a valid IPv4 address");
        }
        long value = 0;
        for (String octet : octets) {
            int parsed;
            try {
                parsed = Integer.parseInt(octet);
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("Discovered address is not a valid IPv4 address", exception);
            }
            if (parsed < 0 || parsed > 255) {
                throw new IllegalStateException("Discovered address is not a valid IPv4 address");
            }
            value = (value << 8) | parsed;
        }
        return value;
    }

    @Transactional(readOnly = true)
    public List<NetworkDiscoverySnapshotDto> getDiscoveryHistory(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, MAX_DISCOVERY_HISTORY));
        return discoverySnapshotRepository.findAllByOrderByObservedAtDesc(PageRequest.of(0, boundedLimit))
            .stream()
            .map(this::toSnapshotDto)
            .toList();
    }

    private void saveDiscoverySnapshot(NetworkInventoryUpdateDto update) {
        NetworkDiscoverySnapshot snapshot = new NetworkDiscoverySnapshot();
        snapshot.setSubnet(update.subnet());
        snapshot.setSource(update.source());
        snapshot.setObservedAt(LocalDateTime.ofInstant(update.observedAt(), ZoneOffset.UTC));
        for (NetworkDetectedDeviceDto device : update.devices()) {
            NetworkDiscoverySnapshotDevice savedDevice = new NetworkDiscoverySnapshotDevice();
            savedDevice.setAddress(device.address());
            savedDevice.setMacAddress(device.macAddress());
            savedDevice.setHostName(device.name());
            savedDevice.setDetectionMethod(device.detectionMethod());
            savedDevice.setStatus(device.status());
            savedDevice.setRegistered(device.registered());
            savedDevice.setFirstDetectedAt(device.firstDetectedAt());
            savedDevice.setLastDetectedAt(device.lastDetectedAt());
            savedDevice.setConsecutiveMisses(device.consecutiveMisses());
            savedDevice.setGateway(device.gateway());
            savedDevice.setGatewayAddress(device.gatewayAddress());
            snapshot.addDevice(savedDevice);
        }
        discoverySnapshotRepository.save(snapshot);
    }

    private NetworkDiscoverySnapshotDto toSnapshotDto(NetworkDiscoverySnapshot snapshot) {
        List<NetworkDetectedDeviceDto> devices = snapshot.getDevices().stream()
            .map(device -> new NetworkDetectedDeviceDto(
                device.getId(),
                device.getHostName(),
                device.getAddress(),
                device.getMacAddress(),
                device.getDetectionMethod(),
                device.getStatus(),
                device.isRegistered(),
                device.getFirstDetectedAt(),
                device.getLastDetectedAt(),
                device.getConsecutiveMisses(),
                device.isGateway(),
                device.getGatewayAddress()
            ))
            .toList();
        Instant observedAt = snapshot.getObservedAt().toInstant(ZoneOffset.UTC);
        return new NetworkDiscoverySnapshotDto(
            snapshot.getId(),
            snapshot.getSubnet(),
            snapshot.getSource(),
            observedAt,
            devices
        );
    }

    @Transactional
    public synchronized NetworkInventoryUpdateDto synchronizeDetectedDevices() {
        LocalNetworkSnapshot snapshot = localNetworkDiscoveryService.readCurrentNeighbors();
        LocalDateTime observedAt = LocalDateTime.ofInstant(snapshot.observedAt(), ZoneOffset.UTC);
        Map<String, NetworkHost> registeredHosts = hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc().stream()
            .collect(Collectors.toMap(host -> host.getAddress().toLowerCase(), host -> host, (left, right) -> left));
        Set<String> observedAddresses = new HashSet<>();
        boolean changed = false;
        String defaultGateway = snapshot.gatewayAddress();

        for (NeighborEntry entry : snapshot.devices()) {
            observedAddresses.add(entry.address().toLowerCase());
            NetworkDetectedDevice device = findDetectedDevice(entry);
            boolean isGateway = (defaultGateway != null && defaultGateway.equalsIgnoreCase(entry.address()))
                || (entry.detectionMethod() != null && entry.detectionMethod().contains("Puerta de enlace"));
            String deviceGatewayAddress = isGateway ? entry.address() : defaultGateway;

            if (device == null) {
                device = new NetworkDetectedDevice();
                device.setAddress(entry.address());
                device.setMacAddress(entry.macAddress());
                device.setSubnet(snapshot.subnet());
                device.setHostName(resolveDetectedName(entry.address(), registeredHosts));
                device.setGateway(isGateway);
                device.setGatewayAddress(deviceGatewayAddress);
                if (!java.util.Objects.equals(device.getDetectionMethod(), entry.detectionMethod())) {
                    device.setDetectionMethod(entry.detectionMethod());
                    changed = true;
                }
                device.setStatus(NetworkHostStatus.ONLINE);
                device.setConsecutiveMisses(0);
                device.setFirstDetectedAt(observedAt);
                device.setLastDetectedAt(observedAt);
                device.setLastStatusChangedAt(observedAt);
                device = detectedDeviceRepository.save(device);
                addDiscoveryEvent(device, NetworkEventType.DEVICE_DETECTED,
                    (isGateway ? "Puerta de enlace detectada: " : "Nuevo dispositivo detectado: ") + entry.address());
                changed = true;
                continue;
            }

            if (isGateway && !device.isGateway()) {
                device.setGateway(true);
                changed = true;
            }
            if (deviceGatewayAddress != null && !java.util.Objects.equals(device.getGatewayAddress(), deviceGatewayAddress)) {
                device.setGatewayAddress(deviceGatewayAddress);
                changed = true;
            }

            if (!device.getAddress().equalsIgnoreCase(entry.address())) {
                String previousAddress = device.getAddress();
                device.setAddress(entry.address());
                device.setHostName(resolveDetectedName(entry.address(), registeredHosts));
                addDiscoveryEvent(device, NetworkEventType.DEVICE_IP_CHANGED,
                    "El dispositivo cambió de IP: " + previousAddress + " → " + entry.address());
                changed = true;
            }
            if (!java.util.Objects.equals(device.getMacAddress(), entry.macAddress())) {
                String previousMac = device.getMacAddress();
                device.setMacAddress(entry.macAddress());
                if (previousMac != null) {
                    addDiscoveryEvent(device, NetworkEventType.DEVICE_MAC_CHANGED,
                        "El dispositivo cambió de MAC: " + previousMac + " → " + entry.macAddress());
                }
                changed = true;
            }
            device.setDetectionMethod(entry.detectionMethod());
            if (device.getStatus() != NetworkHostStatus.ONLINE) {
                device.setStatus(NetworkHostStatus.ONLINE);
                device.setLastStatusChangedAt(observedAt);
                addDiscoveryEvent(device, NetworkEventType.DEVICE_ONLINE,
                    "Dispositivo nuevamente visible en la red");
                changed = true;
            }
            device.setSubnet(snapshot.subnet());
            device.setConsecutiveMisses(0);
            device.setLastDetectedAt(observedAt);
            detectedDeviceRepository.save(device);
        }

        for (NetworkDetectedDevice device : detectedDeviceRepository.findAll()) {
            if (!observedAddresses.contains(device.getAddress().toLowerCase())) {
                device.setConsecutiveMisses(device.getConsecutiveMisses() + 1);
                if (device.getStatus() == NetworkHostStatus.ONLINE) {
                    device.setStatus(NetworkHostStatus.OFFLINE);
                    device.setLastStatusChangedAt(observedAt);
                    addDiscoveryEvent(device, NetworkEventType.DEVICE_DISCONNECTED,
                        "Dispositivo no aparece en la tabla de vecinos ARP activos");
                    changed = true;
                }
                detectedDeviceRepository.save(device);
            }
        }

        List<NetworkDetectedDeviceDto> currentDevices = getDetectedDevices().stream()
            .filter(device -> device.status() == NetworkHostStatus.ONLINE)
            .filter(device -> observedAddresses.contains(device.address().toLowerCase()))
            .toList();
        return new NetworkInventoryUpdateDto(
            "inventory",
            snapshot.subnet(),
            snapshot.source(),
            snapshot.observedAt(),
            currentDevices,
            changed
        );
    }

    private NetworkEventDto toDto(NetworkDiscoveryEvent event) {
        return new NetworkEventDto(
            event.getId(),
            null,
            event.getDeviceName(),
            event.getEventType(),
            event.getDescription(),
            event.getCreatedAt()
        );
    }

    private void addDiscoveryEvent(
        NetworkDetectedDevice device,
        NetworkEventType eventType,
        String description
    ) {
        NetworkDiscoveryEvent event = new NetworkDiscoveryEvent();
        event.setDevice(device);
        event.setAddress(device.getAddress());
        event.setDeviceName(device.getHostName());
        event.setEventType(eventType);
        event.setDescription(description);
        event.setCreatedAt(now());
        discoveryEventRepository.save(event);
    }

    private NetworkDetectedDevice findDetectedDevice(NeighborEntry entry) {
        NetworkDetectedDevice byAddress = detectedDeviceRepository.findByAddressIgnoreCase(entry.address())
            .orElse(null);
        if (byAddress != null) {
            return byAddress;
        }
        return entry.macAddress() == null
            ? null
            : detectedDeviceRepository.findFirstByMacAddressIgnoreCase(entry.macAddress()).orElse(null);
    }

    private String resolveDetectedName(String address, Map<String, NetworkHost> registeredHosts) {
        NetworkHost registeredHost = registeredHosts.get(address.toLowerCase());
        if (registeredHost != null) {
            return registeredHost.getDisplayName();
        }

        Future<String> lookup = HOSTNAME_LOOKUP_EXECUTOR.submit(() ->
            InetAddress.getByName(address).getCanonicalHostName());
        try {
            String hostName = lookup.get(HOSTNAME_LOOKUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            return hostName.isBlank() ? address : hostName;
        } catch (InterruptedException exception) {
            lookup.cancel(true);
            Thread.currentThread().interrupt();
            LOGGER.warn("Hostname lookup was interrupted for {}", address, exception);
        } catch (ExecutionException | TimeoutException exception) {
            lookup.cancel(true);
            LOGGER.debug("No hostname resolved for {} within the lookup timeout", address, exception);
        }
        return address;
    }

    private List<NetworkDetectedDeviceDto> mapDetectedDevices(List<NetworkDetectedDevice> devices) {
        Set<String> registeredAddresses = hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc().stream()
            .map(host -> host.getAddress().toLowerCase())
            .collect(Collectors.toSet());
        return devices.stream()
            .map(device -> new NetworkDetectedDeviceDto(
                device.getId(),
                device.getHostName(),
                device.getAddress(),
                device.getMacAddress(),
                device.getDetectionMethod(),
                device.getStatus(),
                registeredAddresses.contains(device.getAddress().toLowerCase()),
                device.getFirstDetectedAt(),
                device.getLastDetectedAt(),
                device.getConsecutiveMisses(),
                device.isGateway(),
                device.getGatewayAddress()
            ))
            .toList();
    }

    @Transactional
    public Optional<NetworkDetectedDeviceDto> findGateway() {
        Optional<String> activeGatewayAddress = localNetworkDiscoveryService.findGatewayAddress();
        LocalDateTime observedAt = now();
        if (activeGatewayAddress.isPresent()) {
            String address = activeGatewayAddress.get();
            NetworkDetectedDevice device = detectedDeviceRepository.findByAddressIgnoreCase(address)
                .orElseGet(() -> {
                    NetworkDetectedDevice created = new NetworkDetectedDevice();
                    created.setAddress(address);
                    created.setHostName(address);
                    created.setSubnet("");
                    created.setFirstDetectedAt(observedAt);
                    created.setLastStatusChangedAt(observedAt);
                    created.setConsecutiveMisses(0);
                    return created;
                });
            device.setGateway(true);
            device.setGatewayAddress(address);
            device.setStatus(NetworkHostStatus.ONLINE);
            device.setLastDetectedAt(observedAt);
            if (device.getDetectionMethod() == null || device.getDetectionMethod().isBlank()
                || "Sin respuesta actual".equals(device.getDetectionMethod())) {
                device.setDetectionMethod("Puerta de enlace");
            } else if (!device.getDetectionMethod().contains("Puerta de enlace")) {
                device.setDetectionMethod(device.getDetectionMethod() + " + Puerta de enlace");
            }
            device = detectedDeviceRepository.save(device);
            return Optional.of(toDetectedDeviceDto(device));
        }

        return detectedDeviceRepository.findFirstByGatewayTrueOrderByLastDetectedAtDesc()
            .or(() -> detectedDeviceRepository.findAll().stream()
                .filter(d -> d.isGateway() || (d.getDetectionMethod() != null && d.getDetectionMethod().contains("Puerta de enlace")))
                .findFirst()
                .map(d -> {
                    if (!d.isGateway() || d.getGatewayAddress() == null) {
                        d.setGateway(true);
                        d.setGatewayAddress(d.getAddress());
                        return detectedDeviceRepository.save(d);
                    }
                    return d;
                }))
            .map(this::toDetectedDeviceDto);
    }

    private NetworkDetectedDeviceDto toDetectedDeviceDto(NetworkDetectedDevice device) {
        Set<String> registeredAddresses = hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc().stream()
            .map(host -> host.getAddress().toLowerCase())
            .collect(Collectors.toSet());
        return new NetworkDetectedDeviceDto(
            device.getId(),
            device.getHostName(),
            device.getAddress(),
            device.getMacAddress(),
            device.getDetectionMethod(),
            device.getStatus(),
            registeredAddresses.contains(device.getAddress().toLowerCase()),
            device.getFirstDetectedAt(),
            device.getLastDetectedAt(),
            device.getConsecutiveMisses(),
            device.isGateway(),
            device.getGatewayAddress()
        );
    }

    @Transactional
    public void clearHistory() {
        historyLock.writeLock().lock();
        try {
            discoveryEventRepository.deleteAllInBatch();
            discoverySnapshotRepository.deleteAllInBatch();
            eventRepository.deleteAllInBatch();
            portResultRepository.deleteAllInBatch();
            scanRepository.deleteAllInBatch();
            resetHostStatuses(hostRepository.findAll());
        } finally {
            historyLock.writeLock().unlock();
        }
    }

    @Transactional
    public int purgeExpiredHistory() {
        historyLock.readLock().lock();
        try {
            LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC)
                .minus(properties.getHistoryRetentionDays(), ChronoUnit.DAYS);
            int deletedScans = 0;
            List<Long> expiredScanIds = scanRepository.findIdsStartedBefore(
                cutoff,
                PageRequest.of(0, HISTORY_CLEANUP_BATCH_SIZE)
            );
            if (!expiredScanIds.isEmpty()) {
                eventRepository.deleteAllForScans(expiredScanIds);
                portResultRepository.deleteAllForScans(expiredScanIds);
                scanRepository.deleteAllByIdInBatch(expiredScanIds);
                deletedScans = expiredScanIds.size();
            }
            PageRequest batch = PageRequest.of(0, HISTORY_CLEANUP_BATCH_SIZE);
            List<Long> unlinkedEventIds = eventRepository.findUnlinkedIdsBefore(cutoff, batch);
            if (!unlinkedEventIds.isEmpty()) {
                eventRepository.deleteAllByIdInBatch(unlinkedEventIds);
            }
            List<Long> discoveryEventIds = discoveryEventRepository.findIdsCreatedBefore(cutoff, batch);
            if (!discoveryEventIds.isEmpty()) {
                discoveryEventRepository.deleteAllByIdInBatch(discoveryEventIds);
            }
            List<Long> snapshotIds = discoverySnapshotRepository.findIdsObservedBefore(cutoff, batch);
            if (!snapshotIds.isEmpty()) {
                discoverySnapshotRepository.deleteAllByIdInBatch(snapshotIds);
            }
            return deletedScans + unlinkedEventIds.size() + discoveryEventIds.size() + snapshotIds.size();
        } finally {
            historyLock.readLock().unlock();
        }
    }

    @Transactional
    public void clearHostHistory(Long hostId) {
        NetworkHost host = hostRepository.findById(hostId)
            .filter(NetworkHost::isManagedByUi)
            .orElseThrow(() -> new EntityNotFoundException("Managed network host was not found"));
        eventRepository.deleteAllForHost(hostId);
        portResultRepository.deleteAllForHost(hostId);
        scanRepository.deleteAllForHost(hostId);
        resetHostStatus(host);
        hostRepository.save(host);
    }

    @Transactional
    public void deleteSelectedScans(List<Long> scanIds) {
        if (scanIds == null || scanIds.isEmpty() || scanIds.size() > 40
            || scanIds.stream().anyMatch(id -> id == null || id < 1)
            || new HashSet<>(scanIds).size() != scanIds.size()) {
            throw new IllegalArgumentException("Select between 1 and 40 unique scan records");
        }
        List<NetworkScan> scans = scanRepository.findAllById(scanIds);
        if (scans.size() != scanIds.size()) {
            throw new EntityNotFoundException("One or more selected scan records were not found");
        }
        List<Long> existingIds = scans.stream().map(NetworkScan::getId).toList();
        List<Long> hostIds = scans.stream().map(scan -> scan.getHost().getId()).distinct().toList();
        eventRepository.deleteAllForScans(existingIds);
        portResultRepository.deleteAllForScans(existingIds);
        scanRepository.deleteAllByIdInBatch(existingIds);
        for (Long hostId : hostIds) {
            hostRepository.findById(hostId).ifPresent(host -> {
                scanRepository.findFirstByHost_IdOrderByStartedAtDesc(hostId)
                    .ifPresentOrElse(scan -> {
                        host.setStatus(scan.getHostStatus());
                        host.setLatencyMs(scan.getLatencyMs());
                        host.setLastCheckedAt(scan.getCompletedAt());
                    }, () -> resetHostStatus(host));
                hostRepository.save(host);
            });
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void synchronizeAllowedHosts() {
        Set<String> configuredAddresses = new HashSet<>();
        for (NetworkMonitorProperties.AllowedHost target : properties.getAllowedHosts()) {
            String name = target.getName().trim();
            String address = target.getAddress().trim();
            configuredAddresses.add(address.toLowerCase());
            NetworkHost host = hostRepository.findByAddressIgnoreCase(address).orElseGet(NetworkHost::new);
            host.setDisplayName(name);
            host.setAddress(address);
            host.setEnabled(true);
            host.setManagedByUi(false);
            if (host.getId() == null) {
                host.setMonitoredPorts(properties.getDefaultPorts());
            }
            hostRepository.save(host);
        }
        for (NetworkHost host : hostRepository.findAll()) {
            if (host.isEnabled() && !host.isManagedByUi()
                && !configuredAddresses.contains(host.getAddress().toLowerCase())) {
                host.setEnabled(false);
            }
            if (monitoredPortRepository.findAllByHost_IdOrderByPortAsc(host.getId()).isEmpty()) {
                replaceMonitoredPorts(host, host.getMonitoredPorts());
            }
            if (host.isEnabled() && host.getStatus() != NetworkHostStatus.OFFLINE) {
                host.setStatus(NetworkHostStatus.OFFLINE);
                host.setLatencyMs(null);
                host.setConsecutiveFailures(0);
                hostRepository.save(host);
            }
        }
        for (NetworkDetectedDevice device : detectedDeviceRepository.findAll()) {
            if (device.getStatus() != NetworkHostStatus.OFFLINE) {
                device.setStatus(NetworkHostStatus.OFFLINE);
                device.setConsecutiveMisses(0);
                device.setDetectionMethod("Sin respuesta actual");
                detectedDeviceRepository.save(device);
            }
        }
    }

    @Transactional
    public NetworkHostDto registerHost(String name, String address, List<Integer> ports) {
        String normalizedName = name == null ? "" : name.trim();
        String normalizedAddress = address == null ? "" : address.trim();
        if (normalizedName.isBlank() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("Device name must contain between 1 and 100 characters");
        }
        if (!isPrivateIpv4Address(normalizedAddress)) {
            throw new IllegalArgumentException("Enter a private IPv4 address from your local network");
        }
        List<Integer> validatedPorts = validateConfiguredPorts(ports);
        if (properties.getAllowedHosts().stream().anyMatch(target ->
            target.getAddress().trim().equalsIgnoreCase(normalizedAddress))) {
            throw new IllegalArgumentException("This device is already managed by the operator configuration");
        }

        NetworkHost host = hostRepository.findByAddressIgnoreCase(normalizedAddress)
            .orElseGet(NetworkHost::new);
        if (host.getId() != null && !host.isManagedByUi()) {
            throw new IllegalArgumentException("This address is reserved for a configured network device");
        }
        host.setDisplayName(normalizedName);
        host.setAddress(normalizedAddress);
        host.setEnabled(true);
        host.setManagedByUi(true);
        host.setStatus(NetworkHostStatus.OFFLINE);
        host.setLatencyMs(null);
        host.setLastCheckedAt(null);
        host.setConsecutiveFailures(0);
        host.setMonitoredPorts(validatedPorts);
        host = hostRepository.save(host);
        replaceMonitoredPorts(host, validatedPorts);
        return toDto(host);
    }

    @Transactional
    public MonitoredPortDto addMonitoredPort(Long hostId, Integer port) {
        if (port == null || !validPort(port)) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        NetworkHost host = requireManagedHost(hostId);
        if (monitoredPortRepository.existsByHost_IdAndPort(hostId, port)) {
            throw new IllegalArgumentException("This port is already configured for the device");
        }
        if (monitoredPortRepository.countByHost_Id(hostId) >= MAX_CONFIGURED_PORTS) {
            throw new IllegalArgumentException("A device can have at most 32 monitored ports");
        }
        MonitoredPort monitoredPort = new MonitoredPort();
        monitoredPort.setHost(host);
        monitoredPort.setPort(port);
        monitoredPort.setEnabled(true);
        monitoredPort = monitoredPortRepository.save(monitoredPort);
        synchronizeLegacyPortList(host);
        return toDto(monitoredPort);
    }

    @Transactional
    public MonitoredPortDto setMonitoredPortEnabled(Long hostId, Integer port, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("Port monitoring state is required");
        }
        requireManagedHost(hostId);
        MonitoredPort monitoredPort = monitoredPortRepository.findByHost_IdAndPort(hostId, port)
            .orElseThrow(() -> new EntityNotFoundException("Monitored port was not found"));
        monitoredPort.setEnabled(enabled);
        return toDto(monitoredPortRepository.save(monitoredPort));
    }

    @Transactional
    public void removeMonitoredPort(Long hostId, Integer port) {
        NetworkHost host = requireManagedHost(hostId);
        MonitoredPort monitoredPort = monitoredPortRepository.findByHost_IdAndPort(hostId, port)
            .orElseThrow(() -> new EntityNotFoundException("Monitored port was not found"));
        monitoredPortRepository.delete(monitoredPort);
        synchronizeLegacyPortList(host);
    }

    @Transactional
    public NetworkHostDto updateMonitoredPorts(Long hostId, List<Integer> ports) {
        List<Integer> validatedPorts = validateConfiguredPorts(ports);
        NetworkHost host = requireManagedHost(hostId);
        host.setMonitoredPorts(validatedPorts);
        host = hostRepository.save(host);
        replaceMonitoredPorts(host, validatedPorts);
        synchronizeLegacyPortList(host);
        return toDto(host);
    }

    @Transactional
    public void removeManagedHost(Long hostId) {
        NetworkHost host = hostRepository.findById(hostId)
            .filter(NetworkHost::isEnabled)
            .orElseThrow(() -> new EntityNotFoundException("Configured network host was not found"));
        if (!host.isManagedByUi()) {
            throw new IllegalArgumentException("Only devices added from this page can be removed here");
        }
        host.setEnabled(false);
        hostRepository.save(host);
    }

    public NetworkScanDto scanHost(Long hostId, Integer startPort, Integer endPort) {
        if (hostId == null) {
            throw new IllegalArgumentException("hostId is required");
        }
        if (startPort != null || endPort != null) {
            resolvePorts(startPort, endPort);
        }
        historyLock.readLock().lock();
        try {
            ReentrantLock lock = scanLocks.computeIfAbsent(hostId, ignored -> new ReentrantLock());
            if (!lock.tryLock()) {
                throw new ScanRateLimitException("A scan for this host is already running");
            }
            try {
                return scanHostInternal(hostId, startPort, endPort);
            } finally {
                lock.unlock();
            }
        } finally {
            historyLock.readLock().unlock();
        }
    }

    private NetworkScanDto scanHostInternal(Long hostId, Integer startPort, Integer endPort) {
        NetworkHost host = hostRepository.findById(hostId)
            .filter(NetworkHost::isEnabled)
            .orElseThrow(() -> new EntityNotFoundException("Configured network host was not found"));
        requireConfiguredHost(host);
        List<Integer> ports = startPort == null && endPort == null
            ? monitoredPortRepository.findAllByHost_IdAndEnabledTrueOrderByPortAsc(hostId)
                .stream().map(MonitoredPort::getPort).toList()
            : resolvePorts(startPort, endPort);
        try {
            localNetworkDiscoveryService.requireActiveNetwork();
        } catch (NetworkDiscoveryException exception) {
            host.setStatus(NetworkHostStatus.OFFLINE);
            host.setLatencyMs(null);
            host.setLastCheckedAt(now());
            host.setConsecutiveFailures(host.getConsecutiveFailures() + 1);
            hostRepository.save(host);
            throw exception;
        }

        LocalDateTime startedAt = now();
        InetAddress address = resolveAddress(host.getAddress());
        ReachabilityResult reachability = checkReachability(address);
        List<ProbeResult> probeResults = probePorts(address, ports);
        return java.util.Objects.requireNonNull(transactionTemplate.execute(status ->
            persistScan(hostId, ports, probeResults, reachability, startedAt)));
    }

    private NetworkScanDto persistScan(
        Long hostId,
        List<Integer> ports,
        List<ProbeResult> probeResults,
        ReachabilityResult reachability,
        LocalDateTime startedAt
    ) {
        NetworkHost host = hostRepository.findById(hostId)
            .filter(NetworkHost::isEnabled)
            .orElseThrow(() -> new EntityNotFoundException("Configured network host was not found"));
        requireConfiguredHost(host);
        NetworkHostStatus previousHostStatus = host.getStatus();
        boolean reachable = reachability.reachable();
        Integer latencyMs = reachable ? reachability.latencyMs() : null;
        boolean anyOpen = false;
        for (ProbeResult probeResult : probeResults) {
            anyOpen |= probeResult.open();
            if (probeResult.open() && (latencyMs == null || probeResult.latencyMs() < latencyMs)) {
                latencyMs = probeResult.latencyMs();
            }
        }

        boolean responsive = reachable || anyOpen;
        HostProbeState probeState = nextHostProbeState(host.getStatus(), host.getConsecutiveFailures(), responsive);
        int consecutiveFailures = probeState.consecutiveFailures();
        NetworkHostStatus currentStatus = probeState.status();
        LocalDateTime completedAt = now();
        NetworkScan scan = new NetworkScan();
        scan.setHost(host);
        scan.setStartedAt(startedAt);
        scan.setStatus(NetworkScanStatus.COMPLETED);
        scan.setPortsScanned(ports.size());
        scan.setCompletedAt(completedAt);
        scan.setHostStatus(currentStatus);
        scan.setLatencyMs(latencyMs);
        host.setStatus(currentStatus);
        host.setLatencyMs(latencyMs);
        host.setLastCheckedAt(completedAt);
        host.setConsecutiveFailures(consecutiveFailures);
        scan = scanRepository.save(scan);

        if (previousHostStatus != currentStatus && currentStatus != NetworkHostStatus.UNKNOWN) {
            NetworkEventType eventType = currentStatus == NetworkHostStatus.ONLINE
                ? NetworkEventType.HOST_ONLINE
                : NetworkEventType.HOST_OFFLINE;
            addEvent(scan, host, eventType, "Host changed to " + currentStatus);
        }

        Map<Integer, PortScanResult> previousPortResults = ports.isEmpty()
            ? Map.of()
            : portResultRepository.findLatestByHostAndPorts(host.getId(), ports).stream()
                .collect(Collectors.toMap(PortScanResult::getPort, result -> result));
        for (int index = 0; index < ports.size(); index++) {
            int port = ports.get(index);
            ProbeResult probeResult = probeResults.get(index);
            PortStatus currentPortStatus = probeResult.open() ? PortStatus.OPEN : PortStatus.CLOSED;
            PortScanResult previous = previousPortResults.get(port);

            PortScanResult result = new PortScanResult();
            result.setHost(host);
            result.setPort(port);
            result.setServiceName(serviceName(port));
            result.setStatus(currentPortStatus);
            result.setDetectedAt(completedAt);
            scan.addPortResult(result);

            if (previous != null && previous.getStatus() != currentPortStatus) {
                NetworkEventType eventType = currentPortStatus == PortStatus.OPEN
                    ? NetworkEventType.PORT_OPENED
                    : NetworkEventType.PORT_CLOSED;
                addEvent(scan, host, eventType, "Port " + port + " (" + serviceName(port) + ") changed to "
                    + currentPortStatus);
            }
        }

        scanRepository.saveAndFlush(scan);
        return toDto(scan);
    }

    public NetworkScanDto scanManually(Long hostId, Integer startPort, Integer endPort) {
        if (hostId == null) {
            throw new IllegalArgumentException("hostId is required");
        }
        if (startPort != null || endPort != null) {
            resolvePorts(startPort, endPort);
        }
        NetworkHost host = hostRepository.findById(hostId)
            .filter(NetworkHost::isEnabled)
            .orElseThrow(() -> new EntityNotFoundException("Configured network host was not found"));
        requireConfiguredHost(host);

        long currentTime = System.nanoTime();
        AtomicBoolean accepted = new AtomicBoolean();
        nextManualScanAt.compute(hostId, (id, nextAvailable) -> {
            if (nextAvailable == null || currentTime - nextAvailable >= 0) {
                accepted.set(true);
                return currentTime + TimeUnit.SECONDS.toNanos(15);
            }
            return nextAvailable;
        });
        if (!accepted.get()) {
            throw new ScanRateLimitException("Wait before starting another manual scan for this host");
        }
        return scanHost(hostId, startPort, endPort);
    }

    @Transactional(readOnly = true)
    public List<NetworkHostDto> getHosts() {
        return hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc()
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<NetworkScanDto> getHistory(Long hostId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        PageRequest page = PageRequest.of(0, boundedLimit);
        Slice<NetworkScan> scans = hostId == null
            ? scanRepository.findAllByOrderByStartedAtDesc(page)
            : scanRepository.findAllByHost_IdOrderByStartedAtDesc(hostId, page);
        return scans.getContent().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public NetworkDashboardDto getStatus() {
        List<NetworkHost> hosts = hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc();
        List<NetworkHostDto> hostDtos = hosts.stream().map(this::toDto).toList();
        Map<Long, NetworkScan> latestScans = hosts.isEmpty()
            ? Map.of()
            : scanRepository.findLatestByHostIds(hosts.stream().map(NetworkHost::getId).toList()).stream()
                .collect(Collectors.toMap(scan -> scan.getHost().getId(), scan -> scan));
        List<NetworkScanDto> recentScans = hosts.stream()
            .map(host -> latestScans.get(host.getId()))
            .filter(scan -> scan != null)
            .map(this::toDto)
            .toList();
        int online = (int) hosts.stream().filter(host -> host.getStatus() == NetworkHostStatus.ONLINE).count();
        int offline = (int) hosts.stream().filter(host -> host.getStatus() == NetworkHostStatus.OFFLINE).count();
        int openPorts = recentScans.stream()
            .flatMap(scan -> scan.ports().stream())
            .filter(port -> port.status() == PortStatus.OPEN)
            .toList()
            .size();
        List<NetworkEventDto> recentEvents = Stream.concat(
            eventRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toDto),
            discoveryEventRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toDto)
        )
            .sorted(Comparator.comparing(NetworkEventDto::createdAt).reversed())
            .limit(20)
            .toList();
        return new NetworkDashboardDto(hosts.size(), online, offline, openPorts, hostDtos, recentScans, recentEvents);
    }

    @Transactional(readOnly = true)
    public List<Long> getConfiguredHostIds() {
        return hostRepository.findAllByEnabledTrueOrderByDisplayNameAsc().stream()
            .filter(this::isConfigured)
            .map(NetworkHost::getId)
            .toList();
    }

    private void validateConfiguration() {
        if (properties.getConnectTimeoutMillis() < 1 || properties.getConnectTimeoutMillis() > 5_000) {
            throw new IllegalStateException("hexora.network.connect-timeout-millis must be between 1 and 5000");
        }

        if (properties.getMonitorIntervalMs() < 10_000) {
            throw new IllegalStateException("hexora.network.monitor-interval-ms must be at least 10000");
        }
        if (properties.getDiscoveryIntervalMs() < 30_000) {
            throw new IllegalStateException("hexora.network.discovery-interval-ms must be at least 30000");
        }
        if (properties.getHistoryRetentionDays() < 1 || properties.getHistoryRetentionDays() > 3_650) {
            throw new IllegalStateException("hexora.network.history-retention-days must be between 1 and 3650");
        }
        if (properties.getAllowedHosts() == null || properties.getDefaultPorts() == null) {
            throw new IllegalStateException("Network monitoring host and port configuration must not be null");
        }
        Set<String> addresses = new HashSet<>();
        for (NetworkMonitorProperties.AllowedHost target : properties.getAllowedHosts()) {
            if (target.getName() == null || target.getName().isBlank()
                || target.getAddress() == null || target.getAddress().isBlank()) {
                throw new IllegalStateException("Every allowed network host requires a name and address");
            }
            if (!addresses.add(target.getAddress().trim().toLowerCase())) {
                throw new IllegalStateException("Allowed network host addresses must be unique");
            }
        }
        if (properties.getDefaultPorts().isEmpty() || properties.getDefaultPorts().size() > MAX_RANGE_SIZE
            || properties.getDefaultPorts().stream().anyMatch(port -> port == null || !validPort(port))) {
            throw new IllegalStateException("Default port list must contain between 1 and 128 valid TCP ports");
        }
    }

    private void resetHostStatuses(List<NetworkHost> hosts) {
        for (NetworkHost host : hosts) {
            resetHostStatus(host);
        }
        hostRepository.saveAll(hosts);
    }

    private void resetHostStatus(NetworkHost host) {
        host.setStatus(NetworkHostStatus.UNKNOWN);
        host.setLatencyMs(null);
        host.setLastCheckedAt(null);
        host.setConsecutiveFailures(0);
    }

    private List<Integer> resolvePorts(Integer startPort, Integer endPort) {
        if (startPort == null && endPort == null) {
            return List.copyOf(properties.getDefaultPorts());
        }
        if (startPort == null || endPort == null || !validPort(startPort) || !validPort(endPort)
            || endPort < startPort || endPort - startPort + 1 > MAX_RANGE_SIZE) {
            throw new IllegalArgumentException("Port range must be valid, ascending, and no larger than 128 ports");
        }
        return java.util.stream.IntStream.rangeClosed(startPort, endPort).boxed().toList();
    }

    private boolean validPort(int port) {
        return port >= 1 && port <= 65_535;
    }

    private void requireConfiguredHost(NetworkHost host) {
        if (!isConfigured(host)) {
            throw new EntityNotFoundException("Network host is not in the configured allowlist");
        }
    }

    private boolean isConfigured(NetworkHost host) {
        return host.isManagedByUi() || properties.getAllowedHosts().stream().anyMatch(target ->
            target.getAddress().trim().equalsIgnoreCase(host.getAddress())
                && target.getName().trim().equalsIgnoreCase(host.getDisplayName()));
    }

    private List<Integer> validateConfiguredPorts(List<Integer> ports) {
        if (ports == null || ports.isEmpty() || ports.size() > MAX_CONFIGURED_PORTS
            || ports.stream().anyMatch(port -> port == null || !validPort(port))
            || new HashSet<>(ports).size() != ports.size()) {
            throw new IllegalArgumentException("Choose between 1 and 32 unique TCP ports from 1 to 65535");
        }
        return List.copyOf(ports);
    }

    private boolean isPrivateIpv4Address(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        int[] values = new int[4];
        for (int index = 0; index < octets.length; index++) {
            if (!octets[index].matches("0|[1-9][0-9]{0,2}")) {
                return false;
            }
            values[index] = Integer.parseInt(octets[index]);
            if (values[index] > 255) {
                return false;
            }
        }
        return values[0] == 10
            || values[0] == 172 && values[1] >= 16 && values[1] <= 31
            || values[0] == 192 && values[1] == 168;
    }

    private InetAddress resolveAddress(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("Configured network host could not be resolved: " + address, exception);
        }
    }

    private ReachabilityResult checkReachability(InetAddress address) {
        long started = System.nanoTime();
        boolean reachable = reachabilityProbe.isReachable(address, properties.getConnectTimeoutMillis());
        int latency = reachable
            ? (int) Math.max(1, (System.nanoTime() - started) / 1_000_000)
            : 0;
        return new ReachabilityResult(reachable, latency);
    }

    private ProbeResult probe(InetAddress address, int port) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), properties.getConnectTimeoutMillis());
            int latency = (int) Math.max(1, (System.nanoTime() - started) / 1_000_000);
            return new ProbeResult(true, latency);
        } catch (ConnectException | SocketTimeoutException | NoRouteToHostException exception) {
            return new ProbeResult(false, 0);
        } catch (IOException exception) {
            throw new IllegalStateException("TCP probe failed for port " + port, exception);
        }
    }

    private List<ProbeResult> probePorts(InetAddress address, List<Integer> ports) {
        List<java.util.concurrent.Callable<ProbeResult>> probes = ports.stream()
            .<java.util.concurrent.Callable<ProbeResult>>map(port -> () -> probe(address, port))
            .toList();
        List<Future<ProbeResult>> results;
        try {
            results = portProbeExecutor.getThreadPoolExecutor().invokeAll(
                probes,
                PORT_SCAN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );
        } catch (RejectedExecutionException exception) {
            throw new ScanRateLimitException("Network scan capacity is busy; try again shortly");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Network port scan was interrupted", exception);
        }

        List<ProbeResult> probeResults = new ArrayList<>(results.size());
        for (Future<ProbeResult> result : results) {
            if (result.isCancelled()) {
                throw new ScanRateLimitException("Network port scan exceeded its 30-second time limit");
            }
            try {
                probeResults.add(result.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Network port scan was interrupted", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Network port probe failed", exception.getCause());
            }
        }
        return probeResults;
    }

    private void addEvent(NetworkScan scan, NetworkHost host, NetworkEventType eventType, String description) {
        NetworkEvent event = new NetworkEvent();
        event.setHost(host);
        event.setEventType(eventType);
        event.setDescription(description);
        event.setCreatedAt(scan.getCompletedAt());
        scan.addEvent(event);
    }

    private NetworkScanDto toDto(NetworkScan scan) {
        List<PortScanResultDto> ports = scan.getPortResults().stream()
            .map(result -> new PortScanResultDto(
                result.getPort(),
                result.getServiceName(),
                result.getStatus(),
                result.getDetectedAt()
            ))
            .toList();
        List<NetworkEventDto> events = scan.getEvents().stream().map(this::toDto).toList();
        return new NetworkScanDto(
            scan.getId(),
            scan.getHost().getId(),
            scan.getHost().getDisplayName(),
            scan.getHost().getAddress(),
            scan.getStatus(),
            scan.getHostStatus(),
            scan.getLatencyMs(),
            scan.getPortsScanned(),
            scan.getStartedAt(),
            scan.getCompletedAt(),
            ports,
            events
        );
    }

    private NetworkHostDto toDto(NetworkHost host) {
        List<MonitoredPortDto> monitoredPorts = host.getId() == null
            ? List.of()
            : monitoredPortRepository.findAllByHost_IdOrderByPortAsc(host.getId()).stream()
                .map(this::toDto)
                .toList();
        return new NetworkHostDto(
            host.getId(),
            host.getDisplayName(),
            host.getAddress(),
            host.getStatus(),
            host.getLatencyMs(),
            host.getLastCheckedAt(),
            monitoredPorts,
            host.isManagedByUi()
        );
    }

    private MonitoredPortDto toDto(MonitoredPort monitoredPort) {
        return new MonitoredPortDto(
            monitoredPort.getId(),
            monitoredPort.getPort(),
            serviceName(monitoredPort.getPort()),
            monitoredPort.isEnabled()
        );
    }

    private void replaceMonitoredPorts(NetworkHost host, List<Integer> ports) {
        List<MonitoredPort> existing = monitoredPortRepository.findAllByHost_IdOrderByPortAsc(host.getId());
        Set<Integer> remaining = new HashSet<>(ports);
        List<MonitoredPort> retained = new ArrayList<>();
        for (MonitoredPort monitoredPort : existing) {
            if (remaining.remove(monitoredPort.getPort())) {
                monitoredPort.setEnabled(true);
                retained.add(monitoredPort);
            } else {
                monitoredPortRepository.delete(monitoredPort);
            }
        }
        List<MonitoredPort> added = remaining.stream().sorted().map(port -> {
            MonitoredPort monitoredPort = new MonitoredPort();
            monitoredPort.setHost(host);
            monitoredPort.setPort(port);
            monitoredPort.setEnabled(true);
            return monitoredPort;
        }).toList();
        if (!retained.isEmpty()) {
            monitoredPortRepository.saveAll(retained);
        }
        if (!added.isEmpty()) {
            monitoredPortRepository.saveAll(added);
        }
    }

    private void synchronizeLegacyPortList(NetworkHost host) {
        List<Integer> ports = monitoredPortRepository.findAllByHost_IdOrderByPortAsc(host.getId()).stream()
            .map(MonitoredPort::getPort)
            .toList();
        host.setMonitoredPorts(ports);
        hostRepository.save(host);
    }

    private NetworkHost requireManagedHost(Long hostId) {
        NetworkHost host = hostRepository.findById(hostId)
            .filter(NetworkHost::isEnabled)
            .orElseThrow(() -> new EntityNotFoundException("Configured network host was not found"));
        requireConfiguredHost(host);
        return host;
    }

    private NetworkEventDto toDto(NetworkEvent event) {
        return new NetworkEventDto(
            event.getId(),
            event.getHost().getId(),
            event.getHost().getDisplayName(),
            event.getEventType(),
            event.getDescription(),
            event.getCreatedAt()
        );
    }

    private String serviceName(int port) {
        return switch (port) {
            case 21 -> "FTP";
            case 22 -> "SSH";
            case 25 -> "SMTP";
            case 53 -> "DNS";
            case 7 -> "Echo";
            case 80 -> "HTTP";
            case 110 -> "POP3";
            case 143 -> "IMAP";
            case 443 -> "HTTPS";
            case 3306 -> "MySQL";
            case 5432 -> "PostgreSQL";
            default -> "Desconocido";
        };
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private record ProbeResult(boolean open, int latencyMs) {
    }

    private record ReachabilityResult(boolean reachable, int latencyMs) {
    }
}
