package com.hexora.network.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.springframework.stereotype.Component;

@Component
public class SocketNetworkProbe implements NetworkProbe {

    private final NetworkReachabilityProbe reachabilityProbe = new NetworkReachabilityProbe();

    @Override
    public boolean isReachable(InetAddress address, int timeoutMillis) {
        return reachabilityProbe.isReachable(address, timeoutMillis);
    }

    @Override
    public NetworkProbeResult probePort(InetAddress address, int port, int timeoutMillis) {
        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), timeoutMillis);
            int latency = (int) Math.max(1, (System.nanoTime() - started) / 1_000_000);
            return new NetworkProbeResult(true, latency);
        } catch (ConnectException | SocketTimeoutException | NoRouteToHostException exception) {
            return new NetworkProbeResult(false, 0);
        } catch (IOException exception) {
            throw new IllegalStateException("TCP probe failed for port " + port, exception);
        }
    }
}
