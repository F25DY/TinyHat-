package com.hexora.network.scheduler;

import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.service.NetworkDiscoveryException;
import com.hexora.network.model.NetworkScanDto;
import com.hexora.network.service.NetworkMonitorProperties;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.service.NetworkScheduledTaskLock;
import com.hexora.network.service.ScanRateLimitException;
import com.hexora.hexora.repository.CuentaRepository;
import com.hexora.hexora.security.TenantTaskExecutor;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import java.util.function.LongConsumer;

@Component
public class NetworkMonitoringScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitoringScheduler.class);
    private final NetworkMonitoringService monitoringService;
    private final NetworkMonitorProperties properties;
    private final NetworkScheduledTaskLock taskLock;
    private final CuentaRepository cuentaRepository;
    private final TenantTaskExecutor tenantTaskExecutor;

    public NetworkMonitoringScheduler(
        NetworkMonitoringService monitoringService,
        NetworkMonitorProperties properties,
        NetworkScheduledTaskLock taskLock,
        CuentaRepository cuentaRepository,
        TenantTaskExecutor tenantTaskExecutor
    ) {
        this.monitoringService = monitoringService;
        this.properties = properties;
        this.taskLock = taskLock;
        this.cuentaRepository = cuentaRepository;
        this.tenantTaskExecutor = tenantTaskExecutor;
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
            for (Long hostId : monitoringService.getConfiguredHostIds()) {
                try {
                    NetworkScanDto scan = monitoringService.scanHost(hostId, null, null);
                } catch (IllegalStateException | EntityNotFoundException | DataAccessException
                    | NetworkDiscoveryException | ScanRateLimitException exception) {
                    LOGGER.error(
                        "Scheduled monitoring failed for configured host id {} in account {}",
                        hostId,
                        accountId,
                        exception
                    );
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
                }
            } catch (IllegalStateException | DataAccessException exception) {
                LOGGER.error("Network monitoring history cleanup failed for account {}", accountId, exception);
            }
        });
    }

    private void forEachActiveAccount(String taskName, LongConsumer task) {
        for (Long accountId : cuentaRepository.findActiveAccountIds()) {
            try {
                tenantTaskExecutor.execute(accountId, () -> task.accept(accountId));
            } catch (RuntimeException exception) {
                LOGGER.error("{} failed for account {}", taskName, accountId, exception);
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
