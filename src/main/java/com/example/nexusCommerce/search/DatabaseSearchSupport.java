package com.example.nexusCommerce.search;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells the search service whether this instance is talking to the engine that owns the
 * tsvector column (V30 is a PostgreSQL-only migration).
 *
 * <p>Detected rather than configured: a flag in {@code application.yml} would have to be kept
 * in step with {@code DB_URL} by hand, and a deployment that points at MySQL while the flag
 * still says Postgres would fail every keyword search with a missing-column error instead of
 * falling back to the LIKE path.
 *
 * <p>The answer is cached on the first successful look because it cannot change while the
 * process is alive. A failure to reach the database is not cached: the next search tries
 * again instead of locking the instance onto the slower path for its whole life.
 */
@Slf4j
@Component
public class DatabaseSearchSupport {

    private static final String POSTGRESQL = "PostgreSQL";

    private final DataSource dataSource;
    private volatile Boolean postgres;

    public DatabaseSearchSupport(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean supportsRankedTextSearch() {
        Boolean known = postgres;
        return known != null ? known : detect();
    }

    private boolean detect() {
        try (Connection connection = dataSource.getConnection()) {
            boolean answer = POSTGRESQL.equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
            postgres = answer;
            return answer;
        } catch (SQLException e) {
            log.warn("Search engine could not be detected, this query uses the LIKE path: {}", e.getMessage());
            return false;
        }
    }
}
