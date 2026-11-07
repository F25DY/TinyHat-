package com.hexora.network.service;

import com.hexora.network.model.NetworkDiscoveryDto;
import java.time.Instant;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalNetworkDiscoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalNetworkDiscoveryService.class);
    private static final int MIN_PREFIX_LENGTH = 22;
    private static final int MAX_ACTIVE_SCAN_HOSTS = 1022;
    private static final int ACTIVE_PROBE_TIMEOUT_MILLIS = 1_500;
    private static final int ACTIVE_SCAN_THREADS = 64;
    private static final long ACTIVE_SCAN_TIMEOUT_SECONDS = 90;
    private static final long COMMAND_TIMEOUT_SECONDS = 5;
    private static final long MULTICAST_DISCOVERY_TIMEOUT_MILLIS = 1_500;
    private static final int SSDP_DISCOVERY_PORT = 1900;
    private static final String SSDP_MULTICAST_ADDRESS = "239.255.255.250";
    private static final int MDNS_PORT = 5353;
    private static final String MDNS_MULTICAST_ADDRESS = "224.0.0.251";
    private static final List<String> MDNS_SERVICE_TYPES = List.of(
        "_services._dns-sd._udp.local",
        "_airplay._tcp.local",
        "_raop._tcp.local",
        "_companion-link._tcp.local",
        "_googlecast._tcp.local",
        "_ipp._tcp.local"
    );
    private static final Pattern ARP_ENTRY = Pattern.compile(
        "^\\s*(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+([0-9a-fA-F:-]{17})\\s+(\\S+)\\s*$");
    private static final Pattern LINUX_NEIGHBOR_ENTRY = Pattern.compile(
        "^\\s*(\\d{1,3}(?:\\.\\d{1,3}){3})\\s+dev\\s+\\S+(?:\\s+lladdr\\s+([0-9a-fA-F:-]{17}))?\\s+(\\S+).*$");
    private static final Pattern LINUX_GATEWAY_ENTRY = Pattern.compile("(?m)^\\s*default\\s+via\\s+(\\S+)");
    private static final Pattern SSDP_STATUS_LINE = Pattern.compile("(?im)^HTTP/1\\.[01] 200(?:\\s|$)");
    private static final Pattern SSDP_REQUIRED_HEADER = Pattern.compile("(?im)^(?:ST|USN):\\s*\\S+");
    private final LocalNeighborPresenceTracker presenceTracker = new LocalNeighborPresenceTracker();
    private final NetworkReachabilityProbe reachabilityProbe = new NetworkReachabilityProbe();
    private final NmapHostDiscovery nmapHostDiscovery = new NmapHostDiscovery();

    public NetworkDiscoveryDto discoverLocalNeighbors() {
        LocalNetworkSnapshot snapshot = readCurrentNeighbors();
        LocalNeighborPresenceTracker.Snapshot presence = presenceTracker.observe(
            snapshot.subnet(),
            snapshot.devices().stream().map(NeighborEntry::address).toList(),
            snapshot.observedAt()
        );
        return new NetworkDiscoveryDto(
            snapshot.subnet(),
            snapshot.source(),
            snapshot.observedAt(),
            presence.connected(),
            presence.disconnected()
        );
    }

    public Optional<String> findGatewayAddress() {
        if (isLinux()) {
            try {
                Inet4Address localAddress = getDefaultRouteAddress();
                NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
                if (networkInterface == null || !networkInterface.isUp()) {
                    return Optional.empty();
                }
                String routes = runCommand(
                    List.of("ip", "-4", "route", "show", "default", "dev", networkInterface.getName()),
                    "Linux default-route lookup timed out",
                    "Linux default-route lookup failed"
                );
                return Optional.ofNullable(parseLinuxGatewayAddress(routes, findDefaultPrivateSubnet()));
            } catch (Exception exception) {
                LOGGER.debug("Could not resolve default gateway address", exception);
                return Optional.empty();
            }
        }
        if (!isWindows()) {
            return Optional.empty();
        }
        try {
            LocalSubnet subnet = findDefaultPrivateSubnet();
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(subnet.address());
            if (networkInterface == null || !networkInterface.isUp()) {
                return Optional.empty();
            }
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) {
                return Optional.empty();
            }
            String command = "$gateway = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' "
                + "-InterfaceIndex " + networkInterface.getIndex()
                + " -ErrorAction SilentlyContinue | Sort-Object RouteMetric, InterfaceMetric "
                + "| Select-Object -First 1 -ExpandProperty NextHop; "
                + "if ($gateway) { 'GATEWAY=' + $gateway }";
            Process process = new ProcessBuilder(
                Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
                "-NoProfile", "-NonInteractive", "-Command", command)
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(parseGatewayAddress(output, subnet));
        } catch (Exception exception) {
            LOGGER.debug("Could not resolve default gateway address", exception);
            return Optional.empty();
        }
    }

    LocalNetworkSnapshot readCurrentNeighbors() {
        if (!isWindows() && !isLinux()) {
            throw new NetworkDiscoveryException(
                "Active local network discovery is supported on Windows and Linux.");
        }

        LocalSubnet subnet = findDefaultPrivateSubnet();
        List<String> responsiveAddresses = probeCurrentSubnet(subnet);
        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(subnet.address());
            if (networkInterface == null || !networkInterface.isUp()) {
                throw new NetworkDiscoveryException("No existe una red activa para realizar el escaneo.");
            }
            String output = readNeighborTable(networkInterface);
            String subnetText = longToIpv4(subnet.network()).getHostAddress() + "/" + subnet.prefixLength();
            Map<String, NeighborEntry> currentDevices = new HashMap<>();
            Map<String, NeighborEntry> neighborEntries = new HashMap<>();
            currentDevices.put(
                subnet.address().getHostAddress(),
                localDeviceEntry(subnet.address(), networkInterface.getHardwareAddress())
            );
            List<NeighborEntry> neighborEntriesFound = isWindows()
                ? parseNeighborEntries(output, subnet)
                : parseLinuxNeighborEntries(output, subnet);
            for (NeighborEntry entry : neighborEntriesFound) {
                neighborEntries.put(entry.address(), entry);
                if (isActiveNeighborState(entry.detectionMethod())) {
                    mergeDiscoveredDevice(currentDevices, entry);
                }
            }
            for (String address : responsiveAddresses) {
                NeighborEntry arpEntry = neighborEntries.get(address);
                mergeDiscoveredDevice(currentDevices, new NeighborEntry(
                    address,
                    arpEntry == null ? null : arpEntry.macAddress(),
                    arpEntry == null || arpEntry.macAddress() == null
                        ? "ICMP"
                        : "ICMP + ARP"
                ));
            }
            String gatewayAddress = parseGatewayAddress(output, subnet);
            if (gatewayAddress != null) {
                NeighborEntry gatewayNeighbor = neighborEntries.get(gatewayAddress);
                mergeDiscoveredDevice(currentDevices, new NeighborEntry(
                    gatewayAddress,
                    gatewayNeighbor == null ? null : gatewayNeighbor.macAddress(),
                    "Puerta de enlace"
                ));
            }
            try {
                for (NeighborEntry entry : nmapHostDiscovery.discover(subnet)) {
                    mergeDiscoveredDevice(currentDevices, entry);
                }
            } catch (NetworkDiscoveryException exception) {
                LOGGER.warn("Nmap discovery failed; keeping results from native network probes", exception);
            }
            for (NeighborEntry entry : discoverSsdpDevices(subnet)) {
                mergeDiscoveredDevice(currentDevices, entry);
            }
            for (NeighborEntry entry : discoverMdnsDevices(subnet, networkInterface)) {
                mergeDiscoveredDevice(currentDevices, entry);
            }
            return new LocalNetworkSnapshot(
                subnetText,
                "ICMP + Nmap ARP + tabla de vecinos + SSDP y mDNS de la red local",
                Instant.now(),
                currentDevices.values().stream()
                    .sorted(java.util.Comparator.comparing(NeighborEntry::address))
                    .toList(),
                gatewayAddress
            );
        } catch (SocketException exception) {
            throw new NetworkDiscoveryException("No existe una red activa para realizar el escaneo.", exception);
        }
    }

    static NeighborEntry localDeviceEntry(Inet4Address address, byte[] hardwareAddress) {
        String macAddress = null;
        if (hardwareAddress != null && hardwareAddress.length > 0) {
            StringBuilder formattedAddress = new StringBuilder();
            for (byte octet : hardwareAddress) {
                if (!formattedAddress.isEmpty()) {
                    formattedAddress.append(':');
                }
                formattedAddress.append("%02x".formatted(octet & 0xFF));
            }
            macAddress = formattedAddress.toString();
        }
        return new NeighborEntry(address.getHostAddress(), macAddress, "Este equipo");
    }

    private List<NeighborEntry> discoverSsdpDevices(LocalSubnet subnet) {
        byte[] request = ("M-SEARCH * HTTP/1.1\r\n"
            + "HOST: " + SSDP_MULTICAST_ADDRESS + ":" + SSDP_DISCOVERY_PORT + "\r\n"
            + "MAN: \"ssdp:discover\"\r\n"
            + "MX: 1\r\n"
            + "ST: ssdp:all\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] responseBuffer = new byte[8_192];
        Map<String, NeighborEntry> devices = new HashMap<>();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MULTICAST_DISCOVERY_TIMEOUT_MILLIS);

        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(subnet.address(), 0))) {
            socket.setSoTimeout((int) MULTICAST_DISCOVERY_TIMEOUT_MILLIS);
            InetAddress multicastAddress = InetAddress.getByName(SSDP_MULTICAST_ADDRESS);
            socket.send(new java.net.DatagramPacket(
                request,
                request.length,
                multicastAddress,
                SSDP_DISCOVERY_PORT
            ));

            while (true) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    break;
                }
                socket.setSoTimeout((int) Math.max(1, remainingMillis));
                java.net.DatagramPacket response = new java.net.DatagramPacket(
                    responseBuffer,
                    responseBuffer.length
                );
                try {
                    socket.receive(response);
                } catch (java.net.SocketTimeoutException exception) {
                    break;
                }
                NeighborEntry device = parseSsdpResponse(
                    response.getData(),
                    response.getLength(),
                    response.getAddress(),
                    subnet
                );
                if (device != null) {
                    devices.put(device.address(), device);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("SSDP local-device discovery was unavailable on interface {}",
                subnet.address().getHostAddress(), exception);
        }
        return List.copyOf(devices.values());
    }

    private List<NeighborEntry> discoverMdnsDevices(LocalSubnet subnet, NetworkInterface networkInterface) {
        Map<String, NeighborEntry> devices = new HashMap<>();
        byte[] responseBuffer = new byte[9_000];
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MULTICAST_DISCOVERY_TIMEOUT_MILLIS);
        InetAddress multicastAddress;
        try {
            multicastAddress = InetAddress.getByName(MDNS_MULTICAST_ADDRESS);
        } catch (UnknownHostException exception) {
            throw new NetworkDiscoveryException("The mDNS multicast address could not be resolved", exception);
        }

        try (MulticastSocket socket = new MulticastSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(MDNS_PORT));
            socket.setNetworkInterface(networkInterface);
            socket.setTimeToLive(255);
            socket.joinGroup(new InetSocketAddress(multicastAddress, MDNS_PORT), networkInterface);
            for (String serviceType : MDNS_SERVICE_TYPES) {
                byte[] query = createMdnsServiceQuery(serviceType);
                socket.send(new java.net.DatagramPacket(
                    query,
                    query.length,
                    multicastAddress,
                    MDNS_PORT
                ));
            }

            while (true) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    break;
                }
                socket.setSoTimeout((int) Math.max(1, remainingMillis));
                java.net.DatagramPacket response = new java.net.DatagramPacket(
                    responseBuffer,
                    responseBuffer.length
                );
                try {
                    socket.receive(response);
                } catch (java.net.SocketTimeoutException exception) {
                    break;
                }
                NeighborEntry device = parseMdnsResponse(
                    response.getData(),
                    response.getLength(),
                    response.getAddress(),
                    subnet
                );
                if (device != null) {
                    devices.put(device.address(), device);
                }
            }
        } catch (IOException exception) {
            LOGGER.warn("mDNS local-device discovery was unavailable on interface {}",
                subnet.address().getHostAddress(), exception);
        }
        return List.copyOf(devices.values());
    }

    static byte[] createMdnsServiceQuery(String serviceType) {
        ByteBuffer query = ByteBuffer.allocate(serviceType.length() + 18);
        query.putShort((short) 0);
        query.putShort((short) 0);
        query.putShort((short) 1);
        query.putShort((short) 0);
        query.putShort((short) 0);
        query.putShort((short) 0);
        for (String label : serviceType.split("\\.")) {
            byte[] labelBytes = label.getBytes(StandardCharsets.US_ASCII);
            query.put((byte) labelBytes.length);
            query.put(labelBytes);
        }
        query.put((byte) 0);
        query.putShort((short) 12);
        query.putShort((short) 1);
        return query.array();
    }

    static NeighborEntry parseMdnsResponse(
        byte[] response,
        int length,
        InetAddress sourceAddress,
        LocalSubnet subnet
    ) {
        if (!(sourceAddress instanceof Inet4Address source)
            || !subnet.contains(source)
            || source.equals(subnet.address())
            || length < 12
            || (response[2] & 0x80) == 0) {
            return null;
        }
        return new NeighborEntry(source.getHostAddress(), null, "mDNS");
    }

    private static void mergeDiscoveredDevice(
        Map<String, NeighborEntry> currentDevices,
        NeighborEntry discovered
    ) {
        NeighborEntry current = currentDevices.get(discovered.address());
        currentDevices.put(discovered.address(), new NeighborEntry(
            discovered.address(),
            current == null ? discovered.macAddress() : current.macAddress(),
            current == null
                ? discovered.detectionMethod()
                : current.detectionMethod() + " + " + discovered.detectionMethod()
        ));
    }

    static boolean isActiveNeighborState(String detectionMethod) {
        if (detectionMethod == null || !detectionMethod.startsWith("ARP (vecino ")
            || !detectionMethod.endsWith(")")) {
            return false;
        }
        String state = detectionMethod.substring("ARP (vecino ".length(), detectionMethod.length() - 1);
        return state.equalsIgnoreCase("Reachable")
            || state.equalsIgnoreCase("Delay")
            || state.equalsIgnoreCase("Probe");
    }

    static String parseGatewayAddress(String output, LocalSubnet subnet) {
        for (String line : output.lines().toList()) {
            if (!line.startsWith("GATEWAY=")) {
                continue;
            }
            String address = line.substring("GATEWAY=".length()).trim();
            try {
                Inet4Address gateway = parseIpv4(address);
                if (subnet.contains(gateway)) {
                    return gateway.getHostAddress();
                }
            } catch (UnknownHostException exception) {
                LOGGER.debug("Ignoring invalid default gateway address from Windows", exception);
            }
        }
        return null;
    }

    static String parseLinuxGatewayAddress(String output, LocalSubnet subnet) {
        Matcher matcher = LINUX_GATEWAY_ENTRY.matcher(output);
        if (!matcher.find()) {
            return null;
        }
        try {
            Inet4Address gateway = parseIpv4(matcher.group(1));
            return subnet.contains(gateway) ? gateway.getHostAddress() : null;
        } catch (UnknownHostException exception) {
            LOGGER.debug("Ignoring invalid default gateway address from Linux", exception);
            return null;
        }
    }

    static NeighborEntry parseSsdpResponse(
        byte[] response,
        int length,
        InetAddress sourceAddress,
        LocalSubnet subnet
    ) {
        if (!(sourceAddress instanceof Inet4Address source)
            || !subnet.contains(source)
            || source.equals(subnet.address())) {
            return null;
        }
        String headers = new String(response, 0, length, StandardCharsets.ISO_8859_1);
        if (!SSDP_STATUS_LINE.matcher(headers).find()
            || !SSDP_REQUIRED_HEADER.matcher(headers).find()) {
            return null;
        }
        return new NeighborEntry(source.getHostAddress(), null, "SSDP");
    }

    private LocalSubnet findDefaultPrivateSubnet() {
        LanInterfaceAddress selected = findPreferredPhysicalLanAddress();
        return createSubnet(selected.address(), selected.prefixLength());
    }

    private LanInterfaceAddress findPreferredPhysicalLanAddress() {
        if (isLinux()) {
            try {
                Inet4Address routeAddress = getDefaultRouteAddress();
                NetworkInterface networkInterface = NetworkInterface.getByInetAddress(routeAddress);
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                    throw new NetworkDiscoveryException("No active Linux LAN interface was found");
                }
                return networkInterface.getInterfaceAddresses().stream()
                    .filter(address -> address.getAddress() instanceof Inet4Address)
                    .filter(address -> address.getAddress().equals(routeAddress))
                    .filter(address -> isPrivateIpv4((Inet4Address) address.getAddress()))
                    .filter(address -> address.getNetworkPrefixLength() >= 22
                        && address.getNetworkPrefixLength() <= 30)
                    .map(address -> new LanInterfaceAddress(
                        (Inet4Address) address.getAddress(),
                        address.getNetworkPrefixLength()))
                    .findFirst()
                    .orElseThrow(() -> new NetworkDiscoveryException(
                        "No active private Linux LAN with a /22 to /30 prefix was found"));
            } catch (SocketException exception) {
                throw new NetworkDiscoveryException("Linux LAN interface lookup failed", exception);
            }
        }
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            throw new NetworkDiscoveryException("Windows PowerShell could not be located");
        }

        String command = "$routes = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' "
            + "-ErrorAction SilentlyContinue; "
            + "$candidates = foreach ($address in (Get-NetIPAddress -AddressFamily IPv4 "
            + "-ErrorAction SilentlyContinue | Where-Object AddressState -eq 'Preferred')) { "
            + "$adapter = Get-NetAdapter -InterfaceIndex $address.InterfaceIndex -ErrorAction SilentlyContinue; "
            + "$iface = Get-NetIPInterface -InterfaceIndex $address.InterfaceIndex -AddressFamily IPv4 "
            + "-ErrorAction SilentlyContinue; "
            + "$private = $address.IPAddress -match '^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)'; "
            + "if ($adapter.HardwareInterface -and $adapter.Status -eq 'Up' -and $private "
            + "-and $address.PrefixLength -ge 22 -and $address.PrefixLength -le 30) { "
            + "$route = $routes | Where-Object InterfaceIndex -eq $address.InterfaceIndex "
            + "| Sort-Object RouteMetric | Select-Object -First 1; "
            + "$metric = if ($null -ne $route) { $route.RouteMetric + $iface.InterfaceMetric } "
            + "else { [int]::MaxValue }; "
            + "[PSCustomObject]@{IPAddress=$address.IPAddress; PrefixLength=$address.PrefixLength; "
            + "HasDefaultRoute=($null -ne $route); Metric=$metric} } }; "
            + "$selected = $candidates | Sort-Object @{Expression={ $_.HasDefaultRoute }; Descending=$true}, "
            + "@{Expression={ $_.Metric }; Ascending=$true} | Select-Object -First 1; "
            + "if ($selected) { 'LAN=' + $selected.IPAddress + '|' + $selected.PrefixLength }";

        try {
            Process process = new ProcessBuilder(
                Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
                "-NoProfile", "-NonInteractive", "-Command", command)
                .redirectErrorStream(true)
                .start();
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new NetworkDiscoveryException("Windows LAN interface lookup timed out");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new NetworkDiscoveryException("Windows could not select an active physical LAN interface");
            }
            LanInterfaceAddress selected = parseLanInterfaceAddress(output);
            if (selected == null) {
                throw new NetworkDiscoveryException(
                    "No active physical private IPv4 LAN interface with a /22 to /30 prefix was found");
            }
            return selected;
        } catch (IOException exception) {
            throw new NetworkDiscoveryException("Windows LAN interface lookup failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NetworkDiscoveryException("Windows LAN interface lookup was interrupted", exception);
        }
    }

    static LanInterfaceAddress parseLanInterfaceAddress(String output) {
        for (String line : output.lines().toList()) {
            if (!line.startsWith("LAN=")) {
                continue;
            }
            String[] values = line.substring("LAN=".length()).split("\\|", -1);
            if (values.length != 2) {
                continue;
            }
            try {
                Inet4Address address = parseIpv4(values[0]);
                int prefixLength = Integer.parseInt(values[1]);
                if (isPrivateIpv4(address) && prefixLength >= 22 && prefixLength <= 30) {
                    return new LanInterfaceAddress(address, (short) prefixLength);
                }
            } catch (UnknownHostException | NumberFormatException exception) {
                LOGGER.debug("Ignoring invalid LAN interface selection from Windows", exception);
            }
        }
        return null;
    }

    public void requireActiveNetwork() {
        Inet4Address localAddress;
        try {
            localAddress = getDefaultRouteAddress();
        } catch (NetworkDiscoveryException exception) {
            throw new NetworkDiscoveryException("No existe una red activa para realizar el escaneo.", exception);
        }
        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
            if (networkInterface == null || !networkInterface.isUp()) {
                throw new NetworkDiscoveryException("No existe una red activa para realizar el escaneo.");
            }
        } catch (SocketException exception) {
            throw new NetworkDiscoveryException("No existe una red activa para realizar el escaneo.", exception);
        }
    }

    private Inet4Address getDefaultRouteAddress() {
        byte[] routeProbeAddress = {(byte) 192, 0, 2, 1};
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByAddress(routeProbeAddress), 9);
            InetAddress localAddress = socket.getLocalAddress();
            if (localAddress instanceof Inet4Address ipv4Address && !ipv4Address.isAnyLocalAddress()) {
                return ipv4Address;
            }
        } catch (IOException exception) {
            throw new NetworkDiscoveryException("The active network route could not be determined", exception);
        }
        throw new NetworkDiscoveryException("No active IPv4 network route was found");
    }

    static LocalSubnet createSubnet(Inet4Address localAddress, short prefixLength) {
        if (prefixLength < MIN_PREFIX_LENGTH || prefixLength > 30) {
            throw new NetworkDiscoveryException(
                "Active local-network discovery supports private IPv4 subnets from /22 through /30");
        }
        long mask = (0xFFFF_FFFFL << (32 - prefixLength)) & 0xFFFF_FFFFL;
        long network = ipv4ToLong(localAddress) & mask;
        long broadcast = network | (~mask & 0xFFFF_FFFFL);
        if (broadcast - network <= 2) {
            throw new NetworkDiscoveryException("The active private network has no host addresses");
        }
        return new LocalSubnet(localAddress, prefixLength, network, broadcast);
    }

    static List<String> parseNeighborAddresses(String output, LocalSubnet subnet) {
        return parseNeighborEntries(output, subnet).stream().map(NeighborEntry::address).toList();
    }

    private List<String> probeCurrentSubnet(LocalSubnet subnet) {
        long hostCount = subnet.broadcast() - subnet.network() - 1;
        if (hostCount > MAX_ACTIVE_SCAN_HOSTS) {
            throw new NetworkDiscoveryException(
                "La red tiene demasiadas direcciones para un sondeo seguro. Se admiten subredes desde /22 hasta /30.");
        }

        List<Callable<String>> probes = new ArrayList<>((int) hostCount);
        for (long value = subnet.network() + 1; value < subnet.broadcast(); value++) {
            Inet4Address candidate = longToIpv4(value);
            if (!candidate.equals(subnet.address())) {
                probes.add(() -> {
                    boolean reachable = reachabilityProbe.isWindows()
                        ? reachabilityProbe.pingWindows(candidate, ACTIVE_PROBE_TIMEOUT_MILLIS)
                        : reachabilityProbe.isReachable(candidate, ACTIVE_PROBE_TIMEOUT_MILLIS);
                    return reachable ? candidate.getHostAddress() : null;
                });
            }
        }
        if (probes.isEmpty()) {
            return List.of();
        }

        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(ACTIVE_SCAN_THREADS, probes.size()));
        try {
            List<Future<String>> results = executor.invokeAll(
                probes, ACTIVE_SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<String> responsiveAddresses = new ArrayList<>();
            for (Future<String> result : results) {
                if (result.isCancelled()) {
                    throw new NetworkDiscoveryException(
                        "El sondeo de la red no terminó a tiempo; no se actualizará el inventario.");
                }
                String address = result.get();
                if (address != null) {
                    responsiveAddresses.add(address);
                }
            }
            return responsiveAddresses;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NetworkDiscoveryException("El sondeo de red fue interrumpido.", exception);
        } catch (ExecutionException exception) {
            throw new NetworkDiscoveryException("No se pudo completar el sondeo de la red.", exception);
        } finally {
            executor.shutdownNow();
        }
    }

    static List<NeighborEntry> parseNeighborEntries(String output, LocalSubnet subnet) {
        TreeSet<Long> addresses = new TreeSet<>();
        java.util.Map<Long, String> macAddresses = new java.util.HashMap<>();
        java.util.Map<Long, String> detectionMethods = new java.util.HashMap<>();
        for (String line : output.lines().toList()) {
            Matcher matcher = ARP_ENTRY.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            try {
                Inet4Address address = parseIpv4(matcher.group(1));
                if (subnet.contains(address) && !address.equals(subnet.address())) {
                    long numericAddress = ipv4ToLong(address);
                    addresses.add(numericAddress);
                    macAddresses.put(numericAddress, matcher.group(2).replace('-', ':').toLowerCase(Locale.ROOT));
                    detectionMethods.put(numericAddress,
                        "ARP (vecino " + matcher.group(3) + ")");
                }
            } catch (UnknownHostException exception) {
                // Ignore malformed lines from the operating-system neighbor table.
            }
        }
        return addresses.stream().map(value -> new NeighborEntry(
            longToIpv4(value).getHostAddress(),
            macAddresses.get(value),
            detectionMethods.get(value)))
            .toList();
    }

    static List<NeighborEntry> parseLinuxNeighborEntries(String output, LocalSubnet subnet) {
        TreeSet<Long> addresses = new TreeSet<>();
        Map<Long, String> macAddresses = new HashMap<>();
        Map<Long, String> detectionMethods = new HashMap<>();
        for (String line : output.lines().toList()) {
            Matcher matcher = LINUX_NEIGHBOR_ENTRY.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            try {
                Inet4Address address = parseIpv4(matcher.group(1));
                if (subnet.contains(address) && !address.equals(subnet.address())) {
                    long numericAddress = ipv4ToLong(address);
                    addresses.add(numericAddress);
                    String macAddress = matcher.group(2);
                    macAddresses.put(numericAddress, macAddress == null
                        ? null
                        : macAddress.replace('-', ':').toLowerCase(Locale.ROOT));
                    detectionMethods.put(numericAddress, "ARP (vecino " + matcher.group(3) + ")");
                }
            } catch (UnknownHostException exception) {
                LOGGER.debug("Ignoring invalid IPv4 address in Linux neighbor table", exception);
            }
        }
        return addresses.stream().map(value -> new NeighborEntry(
            longToIpv4(value).getHostAddress(),
            macAddresses.get(value),
            detectionMethods.get(value)
        )).toList();
    }

    private String readNeighborTable(NetworkInterface networkInterface) {
        if (isLinux()) {
            String neighbors = runCommand(
                List.of("ip", "-4", "neigh", "show", "dev", networkInterface.getName()),
                "Linux neighbor table lookup timed out",
                "Linux neighbor table lookup failed"
            );
            String routes = runCommand(
                List.of("ip", "-4", "route", "show", "default", "dev", networkInterface.getName()),
                "Linux default-route lookup timed out",
                "Linux default-route lookup failed"
            );
            String gateway = parseLinuxGatewayAddress(routes, findDefaultPrivateSubnet());
            return neighbors + (gateway == null ? "" : "\nGATEWAY=" + gateway);
        }

        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot == null || systemRoot.isBlank()) {
            throw new NetworkDiscoveryException("Windows PowerShell could not be located");
        }
        String command = "$gateway = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' "
            + "-InterfaceIndex " + networkInterface.getIndex()
            + " -ErrorAction SilentlyContinue | Sort-Object RouteMetric, InterfaceMetric "
            + "| Select-Object -First 1 -ExpandProperty NextHop; "
            + "if ($gateway) { 'GATEWAY=' + $gateway }; "
            + "Get-NetNeighbor -AddressFamily IPv4 -InterfaceIndex "
            + networkInterface.getIndex() + " | "
            + "ForEach-Object { $_.IPAddress + ' ' + $_.LinkLayerAddress + ' ' + $_.State }";
        return runCommand(List.of(
            Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString(),
            "-NoProfile", "-NonInteractive", "-Command", command
        ), "Windows neighbor table lookup timed out", "Windows neighbor table lookup failed");
    }

    private String runCommand(List<String> command, String timeoutMessage, String failureMessage) {
        ExecutorService outputReader = Executors.newSingleThreadExecutor();
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            Future<byte[]> outputFuture = outputReader.submit(process.getInputStream()::readAllBytes);
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new NetworkDiscoveryException(timeoutMessage);
            }
            String output = new String(outputFuture.get(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new NetworkDiscoveryException(failureMessage);
            }
            return output;
        } catch (IOException exception) {
            throw new NetworkDiscoveryException(failureMessage, exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            throw new NetworkDiscoveryException(failureMessage + " (interrupted)", exception);
        } catch (ExecutionException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            throw new NetworkDiscoveryException(failureMessage + " (output could not be read)", exception);
        } finally {
            outputReader.shutdownNow();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    static Inet4Address parseIpv4(String address) throws UnknownHostException {
        String[] octets = address.split("\\.");
        if (octets.length != 4) {
            throw new UnknownHostException("Not an IPv4 address");
        }
        byte[] bytes = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            try {
                int value = Integer.parseInt(octets[index]);
                if (value < 0 || value > 255) {
                    throw new UnknownHostException("Invalid IPv4 octet");
                }
                bytes[index] = (byte) value;
            } catch (NumberFormatException exception) {
                throw new UnknownHostException("Invalid IPv4 octet");
            }
        }
        return (Inet4Address) InetAddress.getByAddress(bytes);
    }

    private static boolean isPrivateIpv4(Inet4Address address) {
        byte[] octets = address.getAddress();
        int first = octets[0] & 0xFF;
        int second = octets[1] & 0xFF;
        return first == 10
            || first == 172 && second >= 16 && second <= 31
            || first == 192 && second == 168;
    }

    static long ipv4ToLong(Inet4Address address) {
        long value = 0;
        for (byte octet : address.getAddress()) {
            value = (value << 8) | (octet & 0xFFL);
        }
        return value;
    }

    static Inet4Address longToIpv4(long value) {
        byte[] address = {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
        try {
            return (Inet4Address) InetAddress.getByAddress(address);
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("A validated IPv4 address could not be constructed", exception);
        }
    }

    record LocalSubnet(Inet4Address address, short prefixLength, long network, long broadcast) {

        boolean contains(Inet4Address candidate) {
            long value = ipv4ToLong(candidate);
            return value > network && value < broadcast;
        }
    }

    record LanInterfaceAddress(Inet4Address address, short prefixLength) {
    }
}
