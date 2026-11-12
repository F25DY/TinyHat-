package com.hexora.network.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;

import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.model.NetworkScanDto;
import com.hexora.network.service.NetworkMonitorProperties;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.service.NetworkScheduledTaskLock;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

    @Mock
    private AuditService auditService;

    @Mock
    private CapabilityService capabilityService;

    @Mock
    private AuditoriaRepository auditoriaRepository;

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
            tenantTaskExecutor,
            auditService,
            capabilityService,
            auditoriaRepository
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

    @Test
    void persistsAnAuditEventWhenScheduledHostMonitoringFails() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        when(cuentaRepository.findActiveAccountIds()).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTaskExecutor).execute(eq(1L), any(Runnable.class));
        allowScheduledMonitoringForProject(19L);
        when(monitoringService.getConfiguredHostIds(Set.of(19L))).thenReturn(List.of(7L));
        when(monitoringService.scanHost(7L, null, null)).thenThrow(new IllegalStateException("test failure"));

        scheduler.monitorConfiguredHosts();

        verify(auditService).networkSchedulerEvent(
            eq(1L),
            eq("SCHEDULED_NETWORK_SCAN_FAILED"),
            eq("ERROR"),
            org.mockito.ArgumentMatchers.<java.util.Map<String, Object>>argThat(details ->
                Long.valueOf(7L).equals(details.get("hostId"))
                    && "IllegalStateException".equals(details.get("failureType"))));
    }

    @Test
    void persistsAnAuditEventWhenScheduledHostMonitoringSucceeds() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        when(cuentaRepository.findActiveAccountIds()).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTaskExecutor).execute(eq(1L), any(Runnable.class));
        allowScheduledMonitoringForProject(19L);
        when(monitoringService.getConfiguredHostIds(Set.of(19L))).thenReturn(List.of(7L));
        when(monitoringService.scanHost(7L, null, null)).thenReturn(new NetworkScanDto(
            12L, 7L, "host", "192.0.2.10", null, null, null, 3, null, null, List.of(), List.of()));

        scheduler.monitorConfiguredHosts();

        verify(auditService).networkSchedulerEvent(
            eq(1L),
            eq("SCHEDULED_NETWORK_SCAN_COMPLETED"),
            eq("OK"),
            org.mockito.ArgumentMatchers.<java.util.Map<String, Object>>argThat(details ->
                    Long.valueOf(7L).equals(details.get("hostId"))
                        && Long.valueOf(12L).equals(details.get("scanId"))
                        && Integer.valueOf(3).equals(details.get("portsScanned"))));
    }

    @Test
    void persistsAnAuditEventWhenRetentionDeletesExpiredHistory() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        when(cuentaRepository.findActiveAccountIds()).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTaskExecutor).execute(eq(1L), any(Runnable.class));
        when(monitoringService.purgeExpiredHistory()).thenReturn(5);

        scheduler.purgeExpiredHistory();

        verify(auditService).networkSchedulerEvent(
            eq(1L),
            eq("SCHEDULED_NETWORK_RETENTION_COMPLETED"),
            eq("OK"),
            org.mockito.ArgumentMatchers.<java.util.Map<String, Object>>argThat(details ->
                    Integer.valueOf(5).equals(details.get("deletedRecords"))));
    }

    @Test
    void doesNotMonitorWhenTheAccountContinuousMonitoringCapabilityIsDisabled() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        when(cuentaRepository.findActiveAccountIds()).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTaskExecutor).execute(eq(1L), any(Runnable.class));
        when(capabilityService.isEnabled(1L, Capability.MODULO_RED)).thenReturn(true);
        when(capabilityService.isEnabled(1L, Capability.MONITOREO_CONTINUO)).thenReturn(false);

        scheduler.monitorConfiguredHosts();

        org.mockito.Mockito.verifyNoInteractions(auditoriaRepository, monitoringService);
    }

    @Test
    void asksForHostsOnlyFromProjectsWithContinuousMonitoringExplicitlyEnabled() {
        when(taskLock.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
        when(cuentaRepository.findActiveAccountIds()).thenReturn(List.of(1L));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(tenantTaskExecutor).execute(eq(1L), any(Runnable.class));
        allowScheduledMonitoringForProject(19L);
        when(monitoringService.getConfiguredHostIds(Set.of(19L))).thenReturn(List.of());

        scheduler.monitorConfiguredHosts();

        verify(auditoriaRepository).findContinuousMonitoringEnabledProjectIds(1L);
        verify(monitoringService).getConfiguredHostIds(Set.of(19L));
    }

    private void allowScheduledMonitoringForProject(long projectId) {
        when(capabilityService.isEnabled(1L, Capability.MODULO_RED)).thenReturn(true);
        when(capabilityService.isEnabled(1L, Capability.MONITOREO_CONTINUO)).thenReturn(true);
        when(auditoriaRepository.findContinuousMonitoringEnabledProjectIds(1L))
            .thenReturn(List.of(projectId));
    }
}
