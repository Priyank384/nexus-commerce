package com.example.nexusCommerce.configs;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Opt-in (FLYWAY_REPAIR=true): repairs the schema history before migrating so a half-applied
 * migration (typically a seed data SQL error) doesn't wedge a local DB. Off by default and
 * explicitly disabled by the prod profile, so an unexpected failure still stops the app.
 */
@Configuration
@ConditionalOnProperty(name = "app.flyway.repair", havingValue = "true")
public class DevFlywayRepairConfig {
    @Bean
    FlywayMigrationStrategy repairThenMigrate() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
