package com.lessonweb.lesson.contract;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

@ActiveProfiles("test")
abstract class MySqlContractTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("lesson_web")
            .withUsername("lesson_web")
            .withPassword("test-password");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
}
