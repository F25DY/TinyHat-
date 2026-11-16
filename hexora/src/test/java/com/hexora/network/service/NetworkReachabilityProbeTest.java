package com.hexora.network.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkReachabilityProbeTest {

    @Test
    void acceptsOnlyEchoRepliesFromTheProbedAddress() {
        assertTrue(NetworkReachabilityProbe.containsEchoReply(
            "Reply from 172.20.10.2: bytes=32 time=1ms TTL=64",
            "172.20.10.2"));
        assertTrue(NetworkReachabilityProbe.containsEchoReply(
            "Respuesta desde 172.20.10.2: bytes=32 tiempo=92ms TTL=64",
            "172.20.10.2"));
        assertFalse(NetworkReachabilityProbe.containsEchoReply(
            "Respuesta desde 172.20.10.1: host de destino inaccesible.",
            "172.20.10.2"));
        assertFalse(NetworkReachabilityProbe.containsEchoReply(
            "Tiempo de espera agotado para esta solicitud.",
            "172.20.10.2"));
    }

}
