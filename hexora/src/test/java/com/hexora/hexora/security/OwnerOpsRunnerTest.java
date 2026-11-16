package com.hexora.hexora.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class OwnerOpsRunnerTest {

    private static final String ACCOUNT_PUBLIC_ID = "account-public-id";

    @Test
    void requiresExplicitEnableFlagAndNonWebApplicationBeforeExecuting() {
        OwnerOpsService service = mock(OwnerOpsService.class);
        OwnerOpsRunner runner = new OwnerOpsRunner(service, new MockEnvironment());

        assertThrows(
            IllegalStateException.class,
            () -> runner.run(new DefaultApplicationArguments(
                "--tinyhat.ops.suspend-cuenta=" + ACCOUNT_PUBLIC_ID
            ))
        );
        verifyNoInteractions(service);
    }

    @Test
    void refusesOwnerOperationsInWebApplicationModeEvenWhenExplicitlyEnabled() {
        OwnerOpsService service = mock(OwnerOpsService.class);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("tinyhat.ops.enabled", "true");
        OwnerOpsRunner runner = new OwnerOpsRunner(service, environment);

        assertThrows(
            IllegalStateException.class,
            () -> runner.run(new DefaultApplicationArguments(
                "--tinyhat.ops.enabled=true",
                "--tinyhat.ops.suspend-cuenta=" + ACCOUNT_PUBLIC_ID
            ))
        );
        verifyNoInteractions(service);
    }

    @Test
    void runsSuspensionOnlyWhenOwnerOperationsAreExplicitlyEnabledInNonWebMode() {
        OwnerOpsService service = mock(OwnerOpsService.class);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("tinyhat.ops.enabled", "true")
            .withProperty("spring.main.web-application-type", "none");
        OwnerOpsRunner runner = new OwnerOpsRunner(service, environment);

        runner.run(new DefaultApplicationArguments(
            "--tinyhat.ops.enabled=true",
            "--tinyhat.ops.suspend-cuenta=" + ACCOUNT_PUBLIC_ID
        ));

        verify(service).suspendCuenta(ACCOUNT_PUBLIC_ID);
    }
}
