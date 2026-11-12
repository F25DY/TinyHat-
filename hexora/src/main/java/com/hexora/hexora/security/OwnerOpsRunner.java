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
    private static final String SUSPEND_USER_PREFIX = "tinyhat.ops.suspend-usuario";
    private static final String REACTIVATE_USER_PREFIX = "tinyhat.ops.reactivate-usuario";
    private static final String CAPABILITY_PREFIX = "tinyhat.ops.set-capability";

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
        boolean suspendUser = args.containsOption(SUSPEND_USER_PREFIX);
        boolean reactivateUser = args.containsOption(REACTIVATE_USER_PREFIX);
        boolean capability = args.containsOption(CAPABILITY_PREFIX);
        if (!suspend && !reactivate && !suspendUser && !reactivateUser && !capability) {
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
        if (suspendUser) {
            executeUser(args, SUSPEND_USER_PREFIX, true);
        }
        if (reactivateUser) {
            executeUser(args, REACTIVATE_USER_PREFIX, false);
        }
        if (capability) {
            executeCapability(args);
        }
    }

    private void executeUser(ApplicationArguments args, String optionName, boolean suspend) {
        for (String publicId : requireValues(args, optionName)) {
            LOGGER.info("Executing owner command: {} usuario {}", suspend ? "suspend" : "reactivate", publicId);
            boolean success = suspend
                ? ownerOpsService.suspendUsuario(publicId)
                : ownerOpsService.reactivateUsuario(publicId);
            LOGGER.info("usuario {} result: {}", publicId, success ? "SUCCESS" : "FAILED");
        }
    }

    /** Value format: cuentaPublicId:capability:true|false (capability = modulo_red, informes_pdf, monitoreo_continuo). */
    private void executeCapability(ApplicationArguments args) {
        for (String value : requireValues(args, CAPABILITY_PREFIX)) {
            String[] parts = value.split(":");
            if (parts.length != 3 || !("true".equals(parts[2]) || "false".equals(parts[2]))) {
                throw new IllegalArgumentException(
                    "--" + CAPABILITY_PREFIX + " requires <cuentaPublicId>:<capability>:<true|false>");
            }
            LOGGER.info("Executing owner command: set-capability {} {}={}", parts[0], parts[1], parts[2]);
            boolean success = ownerOpsService.setCapability(parts[0], parts[1], Boolean.parseBoolean(parts[2]));
            LOGGER.info("set-capability {} result: {}", parts[0], success ? "SUCCESS" : "FAILED");
        }
    }

    private java.util.List<String> requireValues(ApplicationArguments args, String optionName) {
        var values = args.getOptionValues(optionName);
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("--" + optionName + " requires a non-empty value");
        }
        return values;
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
