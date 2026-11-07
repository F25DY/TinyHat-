package com.hexora.network.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalNetworkDiscoveryServiceTest {

    @Test
    void readsCurrentReachableNeighborsOnWindows() {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));

        LocalNetworkSnapshot snapshot;
        try {
            snapshot = new LocalNetworkDiscoveryService().readCurrentNeighbors();
        } catch (NetworkDiscoveryException exception) {
            assumeTrue("No existe una red activa para realizar el escaneo.".equals(exception.getMessage()));
            return;
        }

        assertTrue(snapshot.subnet().contains("/"));
        assertTrue(snapshot.source().contains("ARP"));
    }

    @Test
    void calculatesThePrivateSubnetBounds() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");

        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);

        assertEquals("172.20.10.0", InetAddress.getByAddress(new byte[] {
            (byte) (subnet.network() >>> 24),
            (byte) (subnet.network() >>> 16),
            (byte) (subnet.network() >>> 8),
            (byte) subnet.network()
        }).getHostAddress());
        assertTrue(subnet.contains((Inet4Address) InetAddress.getByName("172.20.10.9")));
    }

    @Test
    void acceptsSubnetsUpToTheSafeActiveProbeLimit() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("10.10.170.109");

        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 22);

        assertTrue(subnet.contains((Inet4Address) InetAddress.getByName("10.10.168.5")));
        assertEquals("10.10.168.0", InetAddress.getByAddress(new byte[] {
            (byte) (subnet.network() >>> 24),
            (byte) (subnet.network() >>> 16),
            (byte) (subnet.network() >>> 8),
            (byte) subnet.network()
        }).getHostAddress());
    }

    @Test
    void selectsOnlyPrivateLanAddressesWithinTheSupportedPrefixRange() throws Exception {
        var selected = LocalNetworkDiscoveryService.parseLanInterfaceAddress("LAN=192.168.1.20|24\n");

        assertEquals("192.168.1.20", selected.address().getHostAddress());
        assertEquals(24, selected.prefixLength());
        assertNull(LocalNetworkDiscoveryService.parseLanInterfaceAddress("LAN=172.21.105.118|32\n"));
        assertNull(LocalNetworkDiscoveryService.parseLanInterfaceAddress("LAN=8.8.8.8|24\n"));
    }

    @Test
    void includesTheLocalComputerWithItsNetworkInterfaceMacAddress() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("192.168.1.20");

        NeighborEntry localDevice = LocalNetworkDiscoveryService.localDeviceEntry(
            localAddress,
            new byte[] {0x00, 0x11, 0x22, 0x33, 0x44, 0x55}
        );

        assertEquals("192.168.1.20", localDevice.address());
        assertEquals("00:11:22:33:44:55", localDevice.macAddress());
        assertEquals("Este equipo", localDevice.detectionMethod());
    }

    @Test
    void includesTheLocalComputerEvenWhenTheNetworkInterfaceHasNoMacAddress() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("192.168.1.20");

        NeighborEntry localDevice = LocalNetworkDiscoveryService.localDeviceEntry(localAddress, null);

        assertEquals("192.168.1.20", localDevice.address());
        assertNull(localDevice.macAddress());
        assertEquals("Este equipo", localDevice.detectionMethod());
    }

    @Test
    void returnsOnlyValidNeighborIpsInsideTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);
        String arpOutput = """
            Interface: 172.20.10.4 --- 0x7
              Internet Address      Physical Address      Type
              172.20.10.9           aa-bb-cc-dd-ee-ff     dynamic
              172.20.10.2           00-11-22-33-44-55     dynamic
              172.20.10.9           aa-bb-cc-dd-ee-ff     dynamic
              172.20.10.4           00-11-22-33-44-55     dynamic
              999.20.10.3           00-11-22-33-44-55     dynamic
              172.20.10.15          ff-ff-ff-ff-ff-ff     static
              172.20.11.1           00-11-22-33-44-55     dynamic
              172.20.10.3           incomplete            dynamic
            """;

        assertEquals(
            List.of("172.20.10.2", "172.20.10.9"),
            LocalNetworkDiscoveryService.parseNeighborAddresses(arpOutput, subnet)
        );
    }

    @Test
    void refusesSubnetsLargerThanTheSafeActiveProbeLimit() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("10.10.170.109");

        assertThrows(NetworkDiscoveryException.class, () ->
            LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 21));
    }

    @Test
    void parsesNeighborMacAddressesAlongsideTheirIps() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);

        var entries = LocalNetworkDiscoveryService.parseNeighborEntries(
            "  172.20.10.9           aa-bb-cc-dd-ee-ff     dynamic",
            subnet
        );

        assertEquals(1, entries.size());
        assertEquals("172.20.10.9", entries.get(0).address());
        assertEquals("aa:bb:cc:dd:ee:ff", entries.get(0).macAddress());
    }

    @Test
    void parsesMacAddressesFromStaleNeighborsWithoutTreatingThemAsReachable() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);

        var entries = LocalNetworkDiscoveryService.parseNeighborEntries(
            "  172.20.10.9           aa-bb-cc-dd-ee-ff     Stale",
            subnet
        );

        assertEquals(1, entries.size());
        assertEquals("aa:bb:cc:dd:ee:ff", entries.get(0).macAddress());
        assertEquals("ARP (vecino Stale)", entries.get(0).detectionMethod());
    }

    @Test
    void parsesLinuxNeighborRowsAndFiltersThemToTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("192.168.1.20");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 24);

        var entries = LocalNetworkDiscoveryService.parseLinuxNeighborEntries("""
            192.168.1.1 dev eth0 lladdr aa:bb:cc:dd:ee:ff REACHABLE
            192.168.1.9 dev eth0 lladdr 00-11-22-33-44-55 STALE
            192.168.1.10 dev eth0 FAILED
            192.168.2.2 dev eth0 lladdr 12:34:56:78:90:ab REACHABLE
            """, subnet);

        assertEquals(3, entries.size());
        assertEquals("192.168.1.1", entries.get(0).address());
        assertEquals("aa:bb:cc:dd:ee:ff", entries.get(0).macAddress());
        assertTrue(LocalNetworkDiscoveryService.isActiveNeighborState(entries.get(0).detectionMethod()));
        assertEquals("192.168.1.9", entries.get(1).address());
        assertEquals("00:11:22:33:44:55", entries.get(1).macAddress());
        assertTrue(!LocalNetworkDiscoveryService.isActiveNeighborState(entries.get(1).detectionMethod()));
        assertNull(entries.get(2).macAddress());
    }

    @Test
    void acceptsActiveWindowsNeighborStatesCaseInsensitively() {
        assertTrue(LocalNetworkDiscoveryService.isActiveNeighborState("ARP (vecino Reachable)"));
        assertTrue(LocalNetworkDiscoveryService.isActiveNeighborState("ARP (vecino delay)"));
        assertTrue(LocalNetworkDiscoveryService.isActiveNeighborState("ARP (vecino Probe)"));
        assertTrue(!LocalNetworkDiscoveryService.isActiveNeighborState("ARP (vecino Stale)"));
        assertTrue(!LocalNetworkDiscoveryService.isActiveNeighborState("ARP (vecino Unreachable)"));
    }

    @Test
    void parsesOnlyDefaultGatewaysInsideTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("192.168.1.20");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 24);

        assertEquals("192.168.1.1", LocalNetworkDiscoveryService.parseGatewayAddress(
            "GATEWAY=192.168.1.1\n", subnet));
        assertNull(LocalNetworkDiscoveryService.parseGatewayAddress(
            "GATEWAY=192.168.2.1\n", subnet));
        assertNull(LocalNetworkDiscoveryService.parseGatewayAddress(
            "GATEWAY=not-an-ip\n", subnet));
        assertEquals("192.168.1.1", LocalNetworkDiscoveryService.parseLinuxGatewayAddress(
            "default via 192.168.1.1 dev eth0 proto dhcp metric 100\n", subnet));
        assertNull(LocalNetworkDiscoveryService.parseLinuxGatewayAddress(
            "default via 192.168.2.1 dev eth0\n", subnet));
    }

    @Test
    void acceptsSsdpResponsesFromDevicesInsideTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);
        byte[] response = ("HTTP/1.1 200 OK\r\n"
            + "CACHE-CONTROL: max-age=120\r\n"
            + "ST: urn:schemas-upnp-org:device:Basic:1\r\n"
            + "USN: uuid:device-id::urn:schemas-upnp-org:device:Basic:1\r\n\r\n")
            .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        NeighborEntry device = LocalNetworkDiscoveryService.parseSsdpResponse(
            response,
            response.length,
            InetAddress.getByName("172.20.10.9"),
            subnet
        );

        assertEquals("172.20.10.9", device.address());
        assertEquals("SSDP", device.detectionMethod());
        assertNull(device.macAddress());
    }

    @Test
    void ignoresSsdpResponsesOutsideTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);
        byte[] response = "HTTP/1.1 200 OK\r\nST: ssdp:all\r\n\r\n"
            .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        NeighborEntry device = LocalNetworkDiscoveryService.parseSsdpResponse(
            response,
            response.length,
            InetAddress.getByName("172.20.11.9"),
            subnet
        );

        assertNull(device);
    }

    @Test
    void ignoresUnrelatedUdpResponsesDuringSsdpDiscovery() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);
        byte[] response = "not an SSDP response".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);

        NeighborEntry device = LocalNetworkDiscoveryService.parseSsdpResponse(
            response,
            response.length,
            InetAddress.getByName("172.20.10.9"),
            subnet
        );

        assertNull(device);
    }

    @Test
    void acceptsMdnsResponsesFromDevicesInsideTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);
        byte[] response = new byte[12];
        response[2] = (byte) 0x84;

        NeighborEntry device = LocalNetworkDiscoveryService.parseMdnsResponse(
            response,
            response.length,
            InetAddress.getByName("172.20.10.9"),
            subnet
        );

        assertEquals("172.20.10.9", device.address());
        assertEquals("mDNS", device.detectionMethod());
    }

    @Test
    void ignoresMdnsQueriesAndResponsesOutsideTheCurrentSubnet() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");
        var subnet = LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 28);
        byte[] query = new byte[12];
        byte[] response = new byte[12];
        response[2] = (byte) 0x84;

        assertNull(LocalNetworkDiscoveryService.parseMdnsResponse(
            query,
            query.length,
            InetAddress.getByName("172.20.10.9"),
            subnet
        ));
        assertNull(LocalNetworkDiscoveryService.parseMdnsResponse(
            response,
            response.length,
            InetAddress.getByName("172.20.11.9"),
            subnet
        ));
    }

    @Test
    void buildsAValidMdnsPtrQuestion() {
        byte[] query = LocalNetworkDiscoveryService.createMdnsServiceQuery("_airplay._tcp.local");

        assertEquals(1, java.nio.ByteBuffer.wrap(query, 4, 2).getShort());
        assertEquals(12, java.nio.ByteBuffer.wrap(query, query.length - 4, 2).getShort());
        assertEquals(1, java.nio.ByteBuffer.wrap(query, query.length - 2, 2).getShort());
    }

    @Test
    void refusesSubnetsLargerThanThePrivateIpv4Limit() throws Exception {
        Inet4Address localAddress = (Inet4Address) InetAddress.getByName("172.20.10.4");

        assertThrows(NetworkDiscoveryException.class, () ->
            LocalNetworkDiscoveryService.createSubnet(localAddress, (short) 7));
    }
}
