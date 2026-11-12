package com.hexora.network.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class NetworkScanLockServiceTest {

    @Test
    void runsOperationAndReleasesProjectAndGlobalLocksOnTheSameConnection() throws Exception {
        LockHarness harness = new LockHarness(1, 1, 1, 1);
        AtomicBoolean ran = new AtomicBoolean();

        String result = harness.service.executeWithGlobalAndProjectLocks(4, 9, () -> {
            ran.set(true);
            return "complete";
        });

        assertTrue(ran.get());
        org.junit.jupiter.api.Assertions.assertEquals("complete", result);
        harness.verifyPrepared(
            "SELECT GET_LOCK(?, 0)",
            "SELECT GET_LOCK(?, 0)",
            "SELECT RELEASE_LOCK(?)",
            "SELECT RELEASE_LOCK(?)");
    }

    @Test
    void releasesGlobalLockWhenProjectLockIsBusy() throws Exception {
        LockHarness harness = new LockHarness(1, 0, 1);
        AtomicBoolean ran = new AtomicBoolean();

        Boolean result = harness.service.executeWithGlobalAndProjectLocks(4, 9, () -> {
            ran.set(true);
            return true;
        });

        org.junit.jupiter.api.Assertions.assertNull(result);
        assertFalse(ran.get());
        harness.verifyPrepared(
            "SELECT GET_LOCK(?, 0)",
            "SELECT GET_LOCK(?, 0)",
            "SELECT RELEASE_LOCK(?)");
    }

    @Test
    void releasesBothLocksWhenTheScanOperationFails() throws Exception {
        LockHarness harness = new LockHarness(1, 1, 1, 1);
        IllegalStateException failure = new IllegalStateException("scan failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
            harness.service.executeWithGlobalAndProjectLocks(4, 9, () -> {
                throw failure;
            }));

        assertSame(failure, thrown);
        harness.verifyPrepared(
            "SELECT GET_LOCK(?, 0)",
            "SELECT GET_LOCK(?, 0)",
            "SELECT RELEASE_LOCK(?)",
            "SELECT RELEASE_LOCK(?)");
    }

    @Test
    void surfacesLockReleaseFailureInsteadOfReportingASuccessfulScan() throws Exception {
        LockHarness harness = new LockHarness(1, 1, 0, 1);

        assertThrows(SQLException.class, () ->
            harness.service.executeWithGlobalAndProjectLocks(4, 9, () -> "success-shaped fallback"));

        harness.verifyPrepared(
            "SELECT GET_LOCK(?, 0)",
            "SELECT GET_LOCK(?, 0)",
            "SELECT RELEASE_LOCK(?)",
            "SELECT RELEASE_LOCK(?)");
    }

    private static final class LockHarness {

        private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        private final Connection connection = mock(Connection.class);
        private final NetworkScanLockService service;
        private final Queue<String> sqlOrder = new ArrayDeque<>();
        private final Queue<PreparedStatement> statements = new ArrayDeque<>();

        private LockHarness(int... lockResults) throws Exception {
            TinyHatNetworkPolicy policy = mock(TinyHatNetworkPolicy.class);
            when(policy.scanCooldownSeconds()).thenReturn(60);
            service = new NetworkScanLockService(jdbcTemplate, java.time.Clock.systemUTC(), policy);
            when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Object>>any()))
                .thenAnswer(invocation -> {
                    ConnectionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInConnection(connection);
                });
            for (int lockResult : lockResults) {
                PreparedStatement statement = mock(PreparedStatement.class);
                ResultSet resultSet = mock(ResultSet.class);
                statements.add(statement);
                when(statement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(lockResult >= 0);
                when(resultSet.getInt(1)).thenReturn(lockResult);
            }
            when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
                sqlOrder.add(invocation.getArgument(0));
                return statements.remove();
            });
        }

        private void verifyPrepared(String... expectedStatements) throws Exception {
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(expectedStatements),
                java.util.List.copyOf(sqlOrder));
            verify(connection, org.mockito.Mockito.times(expectedStatements.length))
                .prepareStatement(anyString());
        }
    }
}
