package com.lessonweb.lesson.config;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    FlywayConfigurationCustomizer flywayDirectJdbcConnection(DataSourceProperties properties) {
        // Druid 1.2.8 treats Flyway's optional performance_schema probe as a
        // fatal pooled-connection error. Migrations therefore use direct JDBC.
        return configuration -> configuration.dataSource(
                properties.determineUrl(),
                properties.determineUsername(),
                properties.determinePassword());
    }
}
