package com.hexora.network.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NetworkScanLockService {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final TinyHatNetworkPolicy policy;

    public NetworkScanLockService(JdbcTemplate jdbcTemplate, Clock clock, TinyHatNetworkPolicy policy) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.policy = policy;
    }

    public boolean acquireProjectCooldown(long accountId, long projectId, Long userId) {
        int cooldownSeconds = policy.scanCooldownSeconds();
        if (accountId < 1 || projectId < 1) {
            throw new IllegalArgumentException("Invalid network scan cooldown parameters");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int inserted = jdbcTemplate.update(
            "INSERT IGNORE INTO bloqueo_escaneo (proyecto_id, cuenta_id, inicio, usuario_id) VALUES (?, ?, ?, ?)",
            projectId,
            accountId,
            now,
            userId
        );
        if (inserted == 1) {
            return true;
        }
        return jdbcTemplate.update(
            "UPDATE bloqueo_escaneo SET inicio = ?, usuario_id = ? "
                + "WHERE proyecto_id = ? AND cuenta_id = ? AND inicio <= ?",
            now,
            userId,
            projectId,
            accountId,
            now.minusSeconds(cooldownSeconds)
        ) == 1;
    }

    public <T> T executeWithGlobalAndProjectLocks(long accountId, long projectId, Supplier<T> operation) {
        Objects.requireNonNull(operation, "Network scan operation is required");
        String projectLock = "hexora-scan-" + accountId + "-" + projectId;
        return jdbcTemplate.execute((ConnectionCallback<T>) connection -> {
            if (!acquire(connection, "hexora-network-scan-global")) {
                return null;
            }
            try {
                if (!acquire(connection, projectLock)) {
                    return null;
                }
                try {
                    return operation.get();
                } finally {
                    release(connection, projectLock);
                }
            } finally {
                release(connection, "hexora-network-scan-global");
            }
        });
    }

    private boolean acquire(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private void release(Connection connection, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new SQLException("Could not release network scan lock " + lockName);
                }
            }
        }
    }
}
