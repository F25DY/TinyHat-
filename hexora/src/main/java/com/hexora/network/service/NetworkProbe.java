package com.hexora.network.service;

import java.net.InetAddress;

public interface NetworkProbe {

    boolean isReachable(InetAddress address, int timeoutMillis);

    NetworkProbeResult probePort(InetAddress address, int port, int timeoutMillis);
}
