package com.hexora.network.scheduler;

import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.service.NetworkDiscoveryException;
import com.hexora.network.service.NetworkCapabilityDisabledException;
import com.hexora.network.model.NetworkScanDto;
import com.hexora.network.service.NetworkMonitorProperties;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.service.NetworkScheduledTaskLock;
import com.hexora.network.service.ScanRateLimitException;
import com.hexora.network.service.NetworkScopeRejectedException;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.repository.AuditoriaRepository;
import com.hexora.hexora.security.AuditService;
import com.hexora.hexora.security.TenantTaskExecutor;
import com.hexora.hexora.service.Capability;
import com.hexora.hexora.service.CapabilityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import java.util.function.LongConsumer;
import java.util.Map;

@Component
public class NetworkMonitoringScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitoringScheduler.class);
    private final NetworkMonitoringService monitoringService;
    private final NetworkMonitorProperties properties;
    private final NetworkScheduledTaskLock taskLock;
    private final CuentaRepository cuentaRepository;
    private final TenantTaskExecutor tenantTaskExecutor;
    private final AuditService auditService;
    private final CapabilityService capabilityService;
    private final AuditoriaRepository auditoriaRepository;

    public NetworkMonitoringScheduler(
        NetworkMonitoringService monitoringService,
        NetworkMonitorProperties properties,
        NetworkScheduledTaskLock taskLock,
        CuentaRepository cuentaRepository,
        TenantTaskExecutor tenantTaskExecutor,
        AuditService auditService,
        CapabilityService capabilityService,
        AuditoriaRepository auditoriaRepository
    ) {
        this.monitoringService = monitoringService;
        this.properties = properties;
        this.taskLock = taskLock;
        this.cuentaRepository = cuentaRepository;
        this.tenantTaskExecutor = tenantTaskExecutor;
        this.auditService = auditService;
        this.capabilityService = capabilityService;
        this.auditoriaRepository = auditoriaRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeConfiguredHostsForActiveAccounts() {
        runUnderLock("hexora-network-initialize", () ->
            forEachActiveAccount("Network host initialization", ignored ->
                monitoringService.synchronizeAllowedHosts()));
    }

    @Scheduled(fixedDelayString = "${hexora.network.monitor-interval-ms:30000}")
    public void monitorConfiguredHosts() {
        runUnderLock("hexora-network-monitor", this::monitorConfiguredHostsLocked);
    }

    private void monitorConfiguredHostsLocked() {
        forEachActiveAccount("Scheduled network monitoring", accountId -> {
            if (!capabilityService.isEnabled(accountId, Capability.MODULO_RED)
                || !capabilityService.isEnabled(accountId, Capability.MONITOREO_CONTINUO)) {
                return;
            }
            java.util.Set<Long> enabledProjectIds = new java.util.HashSet<>(
                auditoriaRepository.findContinuousMonitoringEnabledProjectIds(accountId));
            for (Long hostId : monitoringService.getConfiguredHostIds(enabledProjectIds)) {
                try {
                    NetworkScanDto scan = monitoringService.scanHost(hostId, null, null);
                    auditSchedulerResult(accountId, "SCHEDULED_NETWORK_SCAN_COMPLETED", Map.of(
                        "hostId", hostId,
                        "scanId", scan.id(),
                        "portsScanned", scan.portsScanned()
                    ));
                } catch (IllegalStateException | EntityNotFoundException | DataAccessException
                    | NetworkDiscoveryException | ScanRateLimitException | NetworkScopeRejectedException
                    | NetworkCapabilityDisabledException exception) {
                    LOGGER.error(
                        "Scheduled monitoring failed for configured host id {} in account {}",
                        hostId,
                        accountId,
                        exception
                    );
                    auditSchedulerFailure(accountId, "SCHEDULED_NETWORK_SCAN_FAILED", hostId, exception);
                }
            }
        });
    }

    @Scheduled(
        fixedDelayString = "${hexora.network.discovery-interval-ms:30000}",
        initialDelayString = "${hexora.network.discovery-initial-delay-ms:1000}"
    )
    public void discoverConnectedDevices() {
        if (!properties.isDiscoveryEnabled()) {
            return;
        }
        runUnderLock("hexora-network-discovery", this::discoverConnectedDevicesLocked);
    }

    private void discoverConnectedDevicesLocked() {
        LOGGER.warn(
            "Automatic network discovery is disabled until it can run with an explicit authorized project scope"
        );
    }

    @Scheduled(fixedDelayString = "${hexora.network.retention-cleanup-interval-ms:900000}")
    public void purgeExpiredHistory() {
        runUnderLock("hexora-network-retention", this::purgeExpiredHistoryLocked);
    }

    private void purgeExpiredHistoryLocked() {
        forEachActiveAccount("Network history cleanup", accountId -> {
            try {
                int deletedRecords = monitoringService.purgeExpiredHistory();
                if (deletedRecords > 0) {
                    LOGGER.info("Purged {} expired network monitoring records for account {}",
                        deletedRecords, accountId);
                    auditSchedulerResult(accountId, "SCHEDULED_NETWORK_RETENTION_COMPLETED",
                        Map.of("deletedRecords", deletedRecords));
                }
            } catch (IllegalStateException | DataAccessException exception) {
                LOGGER.error("Network monitoring history cleanup failed for account {}", accountId, exception);
                auditSchedulerFailure(accountId, "SCHEDULED_NETWORK_RETENTION_FAILED", null, exception);
            }
        });
    }

    private void auditSchedulerFailure(
        long accountId,
        String eventName,
        Long hostId,
        RuntimeException failure
    ) {
        Map<String, Object> details = hostId == null
            ? Map.of("failureType", failure.getClass().getSimpleName())
            : Map.of("hostId", hostId, "failureType", failure.getClass().getSimpleName());
        auditSchedulerResult(accountId, eventName, details);
    }

    private void auditSchedulerResult(long accountId, String eventName, Map<String, Object> details) {
        try {
            String result = eventName.endsWith("_FAILED") ? "ERROR" : "OK";
            auditService.networkSchedulerEvent(accountId, eventName, result, details);
        } catch (RuntimeException auditFailure) {
            LOGGER.error("Could not persist scheduler audit event {} for account {}", eventName, accountId,
                auditFailure);
        }
    }

    private void forEachActiveAccount(String taskName, LongConsumer task) {
        for (Long accountId : cuentaRepository.findActiveAccountIds()) {
            try {
                tenantTaskExecutor.execute(accountId, () -> task.accept(accountId));
            } catch (RuntimeException exception) {
                LOGGER.error("{} failed for account {}", taskName, accountId, exception);
                auditSchedulerFailure(accountId, "SCHEDULED_TASK_FAILED", null, exception);
            }
        }
    }

    private void runUnderLock(String lockName, Runnable task) {
        try {
            if (!taskLock.executeIfAvailable(lockName, task)) {
                LOGGER.debug("Skipping scheduled network task {}; another instance holds its lock", lockName);
            }
        } catch (DataAccessException exception) {
            LOGGER.error("Could not acquire scheduled network task lock {}", lockName, exception);
        }
    }
}
