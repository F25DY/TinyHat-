package com.hexora.network.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NetworkScheduledTaskLock {

    private final JdbcTemplate jdbcTemplate;

    public NetworkScheduledTaskLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean executeIfAvailable(String lockName, Runnable task) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            if (!executeLockStatement(connection, "SELECT GET_LOCK(?, 0)", lockName)) {
                return false;
            }
            try {
                task.run();
                return true;
            } finally {
                executeLockStatement(connection, "SELECT RELEASE_LOCK(?)", lockName);
            }
        }));
    }

    private boolean executeLockStatement(Connection connection, String sql, String lockName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, lockName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }
}
