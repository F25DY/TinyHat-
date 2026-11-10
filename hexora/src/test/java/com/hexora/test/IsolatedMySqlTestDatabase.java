package com.hexora.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

public abstract class IsolatedMySqlTestDatabase {

    private static final String PASSWORD = "hexora_test_password";
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.46")
        .withDatabaseName("hexora_test")
        .withUsername("hexora_test")
        .withPassword(PASSWORD);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl);
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
    }
}
