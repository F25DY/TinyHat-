package com.hexora.network.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class NetworkScheduledTaskLockTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSet lockResult;

    @Mock
    private ResultSet releaseResult;

    @Test
    void runsTaskAndReleasesAcquiredLockOnTheSameConnection() throws Exception {
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any())).thenAnswer(invocation -> {
            ConnectionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(lockResult, releaseResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getInt(1)).thenReturn(1);
        when(releaseResult.next()).thenReturn(true);
        when(releaseResult.getInt(1)).thenReturn(1);
        AtomicBoolean ran = new AtomicBoolean();

        boolean acquired = new NetworkScheduledTaskLock(jdbcTemplate)
            .executeIfAvailable("hexora-network-test", () -> ran.set(true));

        assertTrue(acquired);
        assertTrue(ran.get());
        verify(connection).prepareStatement("SELECT GET_LOCK(?, 0)");
        verify(connection).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void skipsTaskWhenAnotherInstanceOwnsTheLock() throws Exception {
        when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Boolean>>any())).thenAnswer(invocation -> {
            ConnectionCallback<Boolean> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(lockResult);
        when(lockResult.next()).thenReturn(true);
        when(lockResult.getInt(1)).thenReturn(0);
        AtomicBoolean ran = new AtomicBoolean();

        boolean acquired = new NetworkScheduledTaskLock(jdbcTemplate)
            .executeIfAvailable("hexora-network-test", () -> ran.set(true));

        assertFalse(acquired);
        assertFalse(ran.get());
    }
}
