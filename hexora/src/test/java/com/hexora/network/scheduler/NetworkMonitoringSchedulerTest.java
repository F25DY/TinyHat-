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
            properties,
            taskLock,
            cuentaRepository,
            tenantTaskExecutor
        );
    }

    @Test
    void doesNotStartAutomaticDiscoveryWithoutAnExplicitProjectScope() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });

        scheduler.discoverConnectedDevices();

        org.mockito.Mockito.verifyNoInteractions(monitoringService, cuentaRepository, tenantTaskExecutor);
    }

    @Test
    void doesNotRunAutomaticDiscoveryWhenItIsDisabled() {
        properties.setDiscoveryEnabled(false);

        scheduler.discoverConnectedDevices();

        org.mockito.Mockito.verifyNoInteractions(monitoringService);
    }

    @Test
    void skipsAutomaticDiscoveryWhenAnotherInstanceHoldsTheLock() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenReturn(false);

        scheduler.discoverConnectedDevices();

        org.mockito.Mockito.verifyNoInteractions(monitoringService);
    }
}
