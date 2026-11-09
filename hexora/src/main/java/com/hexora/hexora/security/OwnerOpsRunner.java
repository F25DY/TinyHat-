package com.hexora.hexora.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class OwnerOpsRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(OwnerOpsRunner.class);
    private static final String SUSPEND_PREFIX = "tinyhat.ops.suspend-cuenta";
    private static final String REACTIVATE_PREFIX = "tinyhat.ops.reactivate-cuenta";

    private final OwnerOpsService ownerOpsService;

    public OwnerOpsRunner(OwnerOpsService ownerOpsService) {
        this.ownerOpsService = ownerOpsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption(SUSPEND_PREFIX)) {
            for (String publicId : args.getOptionValues(SUSPEND_PREFIX)) {
                LOGGER.info("Executing owner command: suspend cuenta {}", publicId);
                boolean success = ownerOpsService.suspendCuenta(publicId);
                LOGGER.info("Suspend cuenta {} result: {}", publicId, success ? "SUCCESS" : "FAILED");
            }
        }
        if (args.containsOption(REACTIVATE_PREFIX)) {
            for (String publicId : args.getOptionValues(REACTIVATE_PREFIX)) {
                LOGGER.info("Executing owner command: reactivate cuenta {}", publicId);
                boolean success = ownerOpsService.reactivateCuenta(publicId);
                LOGGER.info("Reactivate cuenta {} result: {}", publicId, success ? "SUCCESS" : "FAILED");
            }
        }
    }
}

