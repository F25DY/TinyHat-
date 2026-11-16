package com.hexora.network.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Ipv4CidrTest {

    @Test
    void parsesCanonicalLiteralAndChecksContainment() {
        Ipv4Cidr range = Ipv4Cidr.parse("192.168.10.0/24");
        Ipv4Cidr interfaceRange = Ipv4Cidr.parse("192.168.10.64/26");

        assertThat(range.contains(interfaceRange)).isTrue();
        assertThat(range.containsAddress("192.168.10.25")).isTrue();
        assertThat(range.containsAddress("192.168.11.25")).isFalse();
        assertThat(range.isPrivateOrLocal()).isTrue();
    }

    @Test
    void rejectsHostnamesShellSyntaxAndNonCanonicalNetworks() {
        assertThatThrownBy(() -> Ipv4Cidr.parse("host.local/24"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ipv4Cidr.parse("10.0.0.1; calc/24"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ipv4Cidr.parse("192.168.10.1/24"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ipv4Cidr.parse("10.0.0.1 && dir"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidOctetsAndPrefixLengths() {
        assertThatThrownBy(() -> Ipv4Cidr.parse("256.0.0.0/24"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ipv4Cidr.parse("10.0.0.0/33"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ipv4Cidr.parse("010.0.0.0/24"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recognizesOnlyPrivateLoopbackAndLinkLocalRangesAsLocal() {
        assertThat(Ipv4Cidr.parse("172.16.0.0/24").isPrivateOrLocal()).isTrue();
        assertThat(Ipv4Cidr.parse("127.0.0.0/24").isPrivateOrLocal()).isTrue();
        assertThat(Ipv4Cidr.parse("169.254.1.0/24").isPrivateOrLocal()).isTrue();
        assertThat(Ipv4Cidr.parse("8.8.8.0/24").isPrivateOrLocal()).isFalse();
    }
}
