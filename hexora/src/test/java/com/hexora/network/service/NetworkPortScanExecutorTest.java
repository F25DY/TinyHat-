package com.hexora.network.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class NetworkPortScanExecutorTest {

    @Test
    void returnsProbeResultsWithoutOpeningSocketsInTheTest() throws Exception {
        NetworkProbe probe = mock(NetworkProbe.class);
        when(probe.probePort(InetAddress.getByName("192.0.2.10"), 443, 250))
            .thenReturn(new NetworkProbeResult(true, 4));
        ThreadPoolTaskExecutor executor = executor(2, 2);
        try {
            NetworkPortScanExecutor scanner = new NetworkPortScanExecutor(probe, executor, 2);

            List<NetworkProbeResult> results = scanner.scan(
                InetAddress.getByName("192.0.2.10"), List.of(443), 250);

            assertEquals(List.of(new NetworkProbeResult(true, 4)), results);
            verify(probe).probePort(InetAddress.getByName("192.0.2.10"), 443, 250);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void rejectsInvalidOrUnboundedPortListsBeforeSchedulingAnyProbe() throws Exception {
        NetworkProbe probe = mock(NetworkProbe.class);
        ThreadPoolTaskExecutor executor = executor(2, 2);
        try {
            NetworkPortScanExecutor scanner = new NetworkPortScanExecutor(probe, executor, 2);
            InetAddress address = InetAddress.getByName("192.0.2.10");

            assertThrows(IllegalArgumentException.class,
                () -> scanner.scan(address, java.util.Collections.nCopies(33, 443), 250));
            assertThrows(IllegalArgumentException.class,
                () -> scanner.scan(address, List.of(0), 250));
            assertThrows(IllegalArgumentException.class,
                () -> scanner.scan(address, java.util.Arrays.asList((Integer) null), 250));

            verify(probe, never()).probePort(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void cancelsSlowPortTasksAtTheConfiguredTimeout() throws Exception {
        NetworkProbe probe = mock(NetworkProbe.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(probe.probePort(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
                started.countDown();
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(10));
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return new NetworkProbeResult(false, 0);
            });
        ThreadPoolTaskExecutor executor = executor(1, 1);
        try {
            NetworkPortScanExecutor scanner = new NetworkPortScanExecutor(probe, executor, 1);
            long start = System.nanoTime();

            assertThrows(ScanRateLimitException.class,
                () -> scanner.scan(InetAddress.getByName("192.0.2.10"), List.of(443), 100));

            assertTrue(started.await(1, TimeUnit.SECONDS));
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 3);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void mapsExecutorSaturationToARateLimitWithoutRunningAnotherProbe() throws Exception {
        NetworkProbe probe = mock(NetworkProbe.class);
        ThreadPoolTaskExecutor executor = executor(1, 0);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                taskStarted.countDown();
                try {
                    releaseTask.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(taskStarted.await(1, TimeUnit.SECONDS));
            NetworkPortScanExecutor scanner = new NetworkPortScanExecutor(probe, executor, 1);

            assertThrows(ScanRateLimitException.class, () -> scanner.scan(
                InetAddress.getByName("192.0.2.10"), List.of(443), 100));
            verify(probe, never()).probePort(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
        } finally {
            releaseTask.countDown();
            executor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor executor(int poolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("network-probe-test-");
        executor.initialize();
        return executor;
    }
}
