package com.hexora.network.service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class NetworkPortScanExecutor {

    private static final int MAX_PORTS_PER_SCAN = 32;

    private final NetworkProbe networkProbe;
    private final ThreadPoolTaskExecutor executor;
    private final int timeoutSeconds;

    @Autowired
    public NetworkPortScanExecutor(
        NetworkProbe networkProbe,
        @Qualifier("networkPortProbeExecutor") ThreadPoolTaskExecutor executor,
        NetworkMonitorProperties properties
    ) {
        this(networkProbe, executor, properties.getPortScanTimeoutSeconds());
    }

    NetworkPortScanExecutor(NetworkProbe networkProbe, ThreadPoolTaskExecutor executor, int timeoutSeconds) {
        if (timeoutSeconds < 1 || timeoutSeconds > 30) {
            throw new IllegalArgumentException("Port scan timeout must be between 1 and 30 seconds");
        }
        this.networkProbe = networkProbe;
        this.executor = executor;
        this.timeoutSeconds = timeoutSeconds;
    }

    public List<NetworkProbeResult> scan(InetAddress address, List<Integer> ports, int connectTimeoutMillis) {
        if (address == null || ports == null || ports.size() > MAX_PORTS_PER_SCAN
            || ports.stream().anyMatch(port -> port == null || port < 1 || port > 65_535)) {
            throw new IllegalArgumentException("Port scan target and port list must be valid and bounded");
        }
        List<java.util.concurrent.Callable<NetworkProbeResult>> probes = ports.stream()
            .<java.util.concurrent.Callable<NetworkProbeResult>>map(
                port -> () -> networkProbe.probePort(address, port, connectTimeoutMillis))
            .toList();
        List<Future<NetworkProbeResult>> results;
        try {
            results = executor.getThreadPoolExecutor().invokeAll(probes, timeoutSeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException exception) {
            throw new ScanRateLimitException("Network scan capacity is busy; try again shortly");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Network port scan was interrupted", exception);
        }

        List<NetworkProbeResult> probeResults = new ArrayList<>(results.size());
        for (Future<NetworkProbeResult> result : results) {
            if (result.isCancelled()) {
                throw new ScanRateLimitException(
                    "Network port scan exceeded its " + timeoutSeconds + "-second time limit");
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
        return List.copyOf(probeResults);
    }
}
