package com.hexora.network.scheduler;

import com.hexora.network.model.NetworkInventoryUpdateDto;
import com.hexora.network.service.NetworkDiscoveryException;
import com.hexora.network.model.NetworkScanDto;
import com.hexora.network.service.NetworkMonitorProperties;
import com.hexora.network.service.NetworkMonitoringService;
import com.hexora.network.service.NetworkScheduledTaskLock;
import com.hexora.network.service.ScanRateLimitException;
import com.hexora.network.websocket.NetworkMonitorWebSocketHandler;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NetworkMonitoringScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMonitoringScheduler.class);
    private final NetworkMonitoringService monitoringService;
    private final NetworkMonitorWebSocketHandler webSocketHandler;
    private final NetworkMonitorProperties properties;
    private final NetworkScheduledTaskLock taskLock;

    public NetworkMonitoringScheduler(
        NetworkMonitoringService monitoringService,
        NetworkMonitorWebSocketHandler webSocketHandler,
        NetworkMonitorProperties properties,
        NetworkScheduledTaskLock taskLock
    ) {
        this.monitoringService = monitoringService;
        this.webSocketHandler = webSocketHandler;
        this.properties = properties;
        this.taskLock = taskLock;
    }

    @Scheduled(fixedDelayString = "${hexora.network.monitor-interval-ms:30000}")
    public void monitorConfiguredHosts() {
        runUnderLock("hexora-network-monitor", this::monitorConfiguredHostsLocked);
    }

    private void monitorConfiguredHostsLocked() {
        for (Long hostId : monitoringService.getConfiguredHostIds()) {
            try {
                NetworkScanDto scan = monitoringService.scanHost(hostId, null, null);
                webSocketHandler.publish(scan);
            } catch (IllegalStateException | EntityNotFoundException | DataAccessException
                | NetworkDiscoveryException | ScanRateLimitException exception) {
                LOGGER.error("Scheduled monitoring failed for configured host id {}", hostId, exception);
            }
        }
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
        try {
            NetworkInventoryUpdateDto update = monitoringService.synchronizeDetectedDevices();
            webSocketHandler.publish(update);
        } catch (IllegalStateException | DataAccessException | NetworkDiscoveryException exception) {
            LOGGER.error("Automatic local network discovery failed", exception);
        }
    }

    @Scheduled(fixedDelayString = "${hexora.network.retention-cleanup-interval-ms:900000}")
    public void purgeExpiredHistory() {
        runUnderLock("hexora-network-retention", this::purgeExpiredHistoryLocked);
    }

    private void purgeExpiredHistoryLocked() {
        try {
            int deletedRecords = monitoringService.purgeExpiredHistory();
            if (deletedRecords > 0) {
                LOGGER.info("Purged {} expired network monitoring records", deletedRecords);
            }
        } catch (IllegalStateException | DataAccessException exception) {
            LOGGER.error("Network monitoring history cleanup failed", exception);
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
