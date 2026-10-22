package com.example.nexusCommerce.configs;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev-only: repairs the schema history before migrating so a half-applied migration
 * (typically a seed data SQL error) doesn't wedge the local DB. Production stays on
 * the default strategy so an unexpected failure still stops the app.
 */
@Configuration
@Profile("dev")
public class DevFlywayRepairConfig {
    @Bean
    FlywayMigrationStrategy repairThenMigrate() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
