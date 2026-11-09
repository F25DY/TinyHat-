package com.hexora.network.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.service.NetworkMonitorProperties;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.service.NetworkScheduledTaskLock;
import com.hexora.network.websocket.NetworkMonitorWebSocketHandler;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.security.TenantTaskExecutor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkMonitoringSchedulerTest {

    @Mock
    private NetworkMonitoringService monitoringService;

    @Mock
    private NetworkMonitorWebSocketHandler webSocketHandler;

    @Mock
    private NetworkScheduledTaskLock taskLock;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TenantTaskExecutor tenantTaskExecutor;

    private NetworkMonitorProperties properties;
    private NetworkMonitoringScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new NetworkMonitorProperties();
        scheduler = new NetworkMonitoringScheduler(
            monitoringService,
            webSocketHandler,
            properties,
            taskLock,
            cuentaRepository,
            tenantTaskExecutor
        );
    }

    @Test
    void publishesAutomaticConnectedDeviceUpdatesToLiveClients() {
        NetworkInventoryUpdateDto update = new NetworkInventoryUpdateDto(
            "inventory",
            "192.168.1.0/24",
            "ICMP + ARP",
            Instant.parse("2026-10-27T12:00:00Z"),
            List.of(),
            false
        );
        when(monitoringService.synchronizeDetectedDevices()).thenReturn(update);
        when(cuentaRepository.findActiveAccountIds()).thenReturn(List.of(1L, 2L));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTaskExecutor).execute(org.mockito.ArgumentMatchers.anyLong(), any(Runnable.class));
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });

        scheduler.discoverConnectedDevices();

        verify(webSocketHandler, times(2)).publish(update);
        verify(tenantTaskExecutor).execute(org.mockito.ArgumentMatchers.eq(1L), any(Runnable.class));
        verify(tenantTaskExecutor).execute(org.mockito.ArgumentMatchers.eq(2L), any(Runnable.class));
    }

    @Test
    void doesNotRunAutomaticDiscoveryWhenItIsDisabled() {
        properties.setDiscoveryEnabled(false);

        scheduler.discoverConnectedDevices();

        org.mockito.Mockito.verifyNoInteractions(monitoringService, webSocketHandler);
    }

    @Test
    void skipsAutomaticDiscoveryWhenAnotherInstanceHoldsTheLock() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenReturn(false);

        scheduler.discoverConnectedDevices();

        org.mockito.Mockito.verifyNoInteractions(monitoringService, webSocketHandler);
    }
}
