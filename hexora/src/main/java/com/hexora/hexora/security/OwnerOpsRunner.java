package com.hexora.hexora.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OwnerOpsRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwnerOpsRunner.class);
    private static final String SUSPEND_PREFIX = "tinyhat.ops.suspend-cuenta";
    private static final String REACTIVATE_PREFIX = "tinyhat.ops.reactivate-cuenta";

    private final OwnerOpsService ownerOpsService;
    private final Environment environment;

    public OwnerOpsRunner(OwnerOpsService ownerOpsService, Environment environment) {
        this.ownerOpsService = ownerOpsService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean suspend = args.containsOption(SUSPEND_PREFIX);
        boolean reactivate = args.containsOption(REACTIVATE_PREFIX);
        if (!suspend && !reactivate) {
            return;
        }
        if (!environment.getProperty("tinyhat.ops.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Owner operations require --tinyhat.ops.enabled=true");
        }
        if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", "servlet"))) {
            throw new IllegalStateException("Owner operations require spring.main.web-application-type=none");
        }
        if (suspend) {
            execute(args, SUSPEND_PREFIX, true);
        }
        if (reactivate) {
            execute(args, REACTIVATE_PREFIX, false);
        }
    }

    private void execute(ApplicationArguments args, String optionName, boolean suspend) {
        var values = args.getOptionValues(optionName);
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("--" + optionName + " requires a non-empty public ID");
        }
        for (String publicId : values) {
            String operation = suspend ? "suspend" : "reactivate";
            LOGGER.info("Executing owner command: {} cuenta {}", operation, publicId);
            boolean success = suspend
                ? ownerOpsService.suspendCuenta(publicId)
                : ownerOpsService.reactivateCuenta(publicId);
            LOGGER.info("{} cuenta {} result: {}", operation, publicId, success ? "SUCCESS" : "FAILED");
        }
    }
}
